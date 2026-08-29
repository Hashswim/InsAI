# 개발 워크플로우

## Generate-Validate: 기능 하나의 라이프사이클

```
1. RED   — docs/test-plan.md 에 케이스 정의 → 테스트 코드 작성 → 스켈레톤은 throw UnsupportedOperationException
           → ./gradlew test 로 "컴파일되고 실패함" 확인
2. GREEN — 구현 → ./gradlew test 전부 통과
3. 검증  — docker compose up -d → ./gradlew bootRun → curl 스모크로 실제 구동 확인
4. 커밋  — 한 기능 = 한 커밋. 메시지에 테스트 수(예: "GREEN, 158/158") 명시
5. 다음 RED
```

## 테스트 계층

| 계층 | 도구 | 대상 |
|---|---|---|
| Domain 단위 | JUnit5 + AssertJ | 엔티티·VO 규칙 |
| Application 단위 | Mockito | 서비스 오케스트레이션·예외 경로 (strict stubbing) |
| Web 슬라이스 | `@WebMvcTest` + MockMvc + `@MockitoBean` | 컨트롤러·검증·에러 봉투·상태코드 |
| Persistence 슬라이스 | `@DataJpaTest` + H2 | 리포지토리 쿼리·매핑·정렬 |
| 통합 | `@SpringBootTest(MOCK)` + MockMvc + H2 + `@Transactional` | HTTP→DB 전 계층 플로우 |

- 베이스 클래스: `support/AbstractJpaTest`, `support/AbstractIntegrationTest`.
- 통합 테스트는 `@Transactional`(롤백) — 공유 H2(`jdbc:h2:mem:insai;DB_CLOSE_DELAY=-1`)를 오염시키지 않기 위함.
- **Testcontainers 는 비활성** — 이 머신 Docker Desktop 과 호환 안 됨(`/info` 400). `build.gradle`/베이스 클래스에 재도입 TODO.
- 절대 개수를 단정하는 리포지토리 테스트는 시더 등이 커밋한 공유 데이터에 취약 → `deleteAll()` 후 검증.

## 실행 명령

```
cd back/insai
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
./gradlew test                              # 전체
./gradlew test --tests "com.insuranceai.form.*"   # 부분
./gradlew bootRun                           # dev 프로파일 (Postgres:5433 + Flyway + 시드)
./gradlew bootRun --args='--spring.jpa.hibernate.ddl-auto=create --spring.flyway.enabled=false'  # 스키마 생성용
```

## Flyway 마이그레이션 작성법 (검증된 절차)

`ddl-auto: validate` 이므로 마이그레이션과 Hibernate 매핑이 정확히 일치해야 한다.

```
1. docker compose up -d
2. psql -h 127.0.0.1 -p 5433 -U insai -d insai -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO insai;"
3. ./gradlew bootRun --args='--spring.jpa.hibernate.ddl-auto=create --spring.flyway.enabled=false'   → 종료
4. pg_dump -h 127.0.0.1 -p 5433 -U insai -d insai --schema-only --no-owner --no-privileges -t <새테이블>
5. 덤프를 정리해 src/main/resources/db/migration/V{n}__<name>.sql 작성
6. DROP SCHEMA 다시 → ./gradlew bootRun (일반) → Flyway 적용 + validate 통과 확인
```

기존: `V1__init.sql`(auth+collab), `V2__form.sql`(form).

## 로컬 인프라 주의

- 호스트 5432 는 로컬 Homebrew Postgres 가 점유 → docker Postgres 는 **5433** 매핑.
- `pg_dump`/`psql` 은 `/opt/homebrew/opt/postgresql@17/bin` (PATH에 있음). 접속: `127.0.0.1:5433 / insai / insai`.
- macOS 에 `timeout` 명령 없음 (`gtimeout` 사용하거나 백그라운드 + poll).

## 커밋·브랜치 규칙

- 작업 브랜치: `dev`. `main`/`test` 는 배포/QA용.
- 원격: `https://github.com/Hashswim/AI_FInancial_Challenge` (HTTPS).
- 커밋 저자: `Hashswim <ss991117@naver.com>` (repo-local git config).
- 메시지: `<type>(<scope>): <요약>` + 본문에 변경 요점·검증 결과. type = feat/fix/refactor/test/chore/docs.

## MSA 분리 시 체크리스트

1. 해당 모듈의 `application` 포트가 다른 모듈 엔티티를 직접 참조하지 않는지 확인
2. 전용 DB 스키마 + 전용 Flyway 경로로 분리
3. 모듈 간 호출을 in-process 어댑터 → HTTP/메시지 어댑터로 교체
4. 게이트웨이 라우팅 + JWKS 검증 추가
5. 통합 테스트를 계약 테스트(consumer-driven)로 승격
