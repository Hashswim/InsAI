# 모듈: back/insai · com.insuranceai.common

## 목적

전 모듈이 공유하는 기반 요소. 도메인 로직 없음.

## 경로

- `back/insai/src/main/java/com/insuranceai/common/`
- 설정: `back/insai/src/main/resources/application.yml` (+ `-dev`, `src/test/resources/application-test.yml`)

## 핵심 타입

| 타입 | 역할 |
|---|---|
| `common.web.ApiResponse<T>` | 응답 봉투. `ok(data)` / `ok()` / `fail(code,msg)` + 중첩 `ErrorBody` |
| `common.web.PingController` | `GET /api/ping` (헬스 스켈레톤) |
| `common.error.ErrorCode` | enum: INVALID_INPUT..INTERNAL_ERROR, 각자 `HttpStatus` |
| `common.error.BusinessException` | `ErrorCode` 를 감싼 런타임 예외 |
| `common.error.GlobalExceptionHandler` | `@RestControllerAdvice` — 예외 → HTTP + 봉투 |
| `common.domain.BaseEntity` | `@MappedSuperclass`, 앱 생성 UUID + `Persistable` + Auditing + `requireText()` |
| `common.config.SecurityConfig` | 현재 permitAll (게이트웨이 도입 시 교체) |
| `common.config.JpaConfig` | `@EnableJpaAuditing` |
| `common.persistence.JsonMapConverter` | `Map<String,Object>` ↔ JSON varchar |
| `common.persistence.JsonStringListConverter` | `List<String>` ↔ JSON varchar (null→빈 리스트) |

## 불변식·규칙

- `BaseEntity` 상속 엔티티는 정적 팩터리에서 `assignId()` 호출 필수. 안 하면 id 없이 저장 시도 → 실패.
- `GlobalExceptionHandler` 의 generic `Exception` 핸들러는 500 + 원인 미노출. 새 400 케이스는 `handleMalformedRequest` 목록에 예외 타입 추가.
- `ApiResponse` 는 record. Jackson 이 `success/data/error` 직렬화.

## 의존 관계

- 들어옴: 모든 모듈.
- 나감: 없음 (Spring/Jackson/Hibernate 만).

## 테스트

- 현재 `common` 전용 테스트 클래스 없음. 봉투·예외 매핑은 각 컨트롤러 슬라이스 테스트에서 간접 검증.
- 추가 여지: `ApiResponse` 직렬화, `GlobalExceptionHandler` 단독 슬라이스.

## 주의 (gotchas)

- `SecurityConfig` 가 `spring-boot-starter-security` 를 끌어와서, `UserDetailsServiceAutoConfiguration` 이 부팅 로그에 "generated security password" 를 찍는다 — permitAll 이라 무해.
- JSON 컨버터 컬럼은 `@Column(length=...)` 로 크기 지정 필요 (기본 varchar(255) 초과 위험). form 은 8000/2000 사용.
- `@Converter` 는 Hibernate 가 직접 인스턴스화 → Spring 빈 아님 → `@DataJpaTest` 에서도 동작.

## 상태·TODO

- [x] 봉투·예외·BaseEntity·JSON 컨버터
- [ ] `Account` 를 `BaseEntity` 로 이관 (현재 자체 구현 — 테스트 GREEN 유지 위해 보류)
- [ ] jsonb 컨버터
- [ ] `SecurityConfig` → 게이트웨이 JWT 검증
