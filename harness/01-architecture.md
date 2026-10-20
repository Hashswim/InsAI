# Phase 2 — 시스템·모듈 아키텍처

## 현재 형태: 업무 모듈러 모놀리스 + JEV AI 서비스

업무 기능은 `back/insai` Spring Boot 앱 안에서 **도메인별 패키지 경계**로 나눠 개발한다.
JEV 의료 서류 분석은 `back/ai-service`의 FastAPI 서비스로 분리했다.
Spring은 업무 데이터·권한·AI 결과 저장, FastAPI는 LangChain·모델 호출·출력 검증을 담당한다.
책임과 데이터 소유권의 상세 결정은 [서비스 책임 경계](../docs/ai/service-boundaries.md)를 따른다.
업무 패키지의 추가 서비스 분리는 향후 필요에 따라 진행한다.

```
┌─────────────────────────────────────────────┐
│  front/insai  (Vue 3, JS)  :5173             │
│   Vite dev proxy  /api/* → :8080/*           │
└───────────────┬─────────────────────────────┘
                │ REST (JSON, {success,data,error} 봉투)
┌───────────────▼─────────────────────────────┐
│  back/insai  (Spring Boot 3.5, Java 21) :8080│
│                                             │
│  com.insuranceai/                           │
│   ├── common/     응답봉투·예외·BaseEntity·JSON컨버터 │
│   ├── auth/       계정·JWT(RS256)·로그인       │
│   ├── collab/     케이스·서류요청·메시지·결정·타임라인 │
│   ├── form/       템플릿·제출물 (+ catalog: 생명보험) │
│   ├── document/   파일 저장·읽기              │
│   └── ai/         JEV 내부 API 호출·결과 저장  │
│                                             │
│  PostgreSQL(:5433, docker)  ·  Flyway V1..Vn │
│  Redis(:6379, docker)  — 현재 InMemory 구현 사용 │
└─────────────────────────────────────────────┘
                │ 내부 HTTP: 문서 ID·종류·PDF
                ▼
       back/ai-service (FastAPI) :8085
       JEV / LangChain / Router → Gemini
       원문 없는 운영 정보 → LangSmith
```

기존 Java의 구조화·결정문 평문화 LLM 경로는 아직 남아 있다.
JEV는 FastAPI 경로를 사용하며, 기존 기능의 전환은 별도 작업으로 처리한다.

## 모듈 경계 규칙

- 각 모듈은 `api / application / domain / infrastructure` 하위 패키지를 갖는다.
- **모듈 간 호출은 `application` 레이어의 포트 인터페이스를 통해서만** 한다.
  - 예: `form` → `collab` 은 `form.application.DocumentRequestSubmissionPort` 를 정의하고,
    `form.infrastructure.CollabDocumentRequestSubmissionAdapter` 가 `collab.application.MarkDocumentRequestSubmittedService` 로 위임.
- `common` 은 모든 모듈이 의존 가능. 그 외 모듈 간 직접 엔티티 참조 금지.
- 예외: `Role` enum 은 `auth.domain` 에 있고 `collab`/`form` 이 재사용한다 (값 객체, 추출 시 공유 라이브러리로 승격).

## 향후 MSA 분리 대상

| 서비스 | 포트 | 트리거 |
|---|---|---|
| gateway (Spring Cloud Gateway) | 8080 | 서비스 2개 이상 분리 시. JWT 검증 + `X-User-Id`/`X-User-Roles` 주입을 여기로 이관 |
| auth | 8081 | 독립 배포 필요 시 |
| collab | 8082 | 코어. 가장 마지막 |
| form | 8083 | 템플릿 관리 부하 분리 시 |
| document | 8084 | 파일 I/O 격리 |
| ai | 8085 | JEV는 FastAPI로 분리됨. 나머지 Java AI 경로 전환은 후속 작업 |

분리 시 체크리스트는 [02-workflow.md](02-workflow.md) "MSA 분리" 절.

## 인증·인가 (현재 vs 목표)

| | 현재 | 목표 |
|---|---|---|
| 토큰 | auth 가 RS256 JWT 발급, JWKS 노출 | 동일 |
| 검증 | 각 서비스가 `SecurityConfig` permitAll (검증 안 함) | 게이트웨이가 JWKS로 검증 |
| 행위자 식별 | 클라이언트가 `X-User-Id` 헤더 직접 전송 (신뢰) | 게이트웨이가 검증 후 주입, 다운스트림은 헤더 신뢰 |
| refresh 저장 | `InMemoryRefreshTokenStore` | Redis |

## 관측성 (예정)

Actuator 활성(`/actuator/health`, `/actuator/info`). Prometheus/Grafana/Zipkin 는 미착수.
JEV는 원문 없는 LangSmith 수동 추적을 구현했으며 실제 계정 연결 검증은 별도다.

## 배포 (로컬)

```
docker compose up -d          # Postgres:5433, Redis:6379
cd back/insai && ./gradlew bootRun   # :8080, Flyway 자동 적용 + 생명보험 템플릿 시드
cd front/insai && npm run dev        # :5173
```

JEV 분석 시 FastAPI도 실행해야 한다. Gemini·LangSmith 및 내부 인증 설정은
[JEV 실행 안내](../docs/ai/jev-cycle-acceptance.md)를 참고한다.
