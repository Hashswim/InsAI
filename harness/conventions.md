# 코드·API·보안 규약

## 패키지 구조

```
com.insuranceai.<module>/
├── api/            컨트롤러, 요청/응답 DTO (record)
├── application/    UseCase 서비스(@Service, @Transactional), Command/View(record), 포트 인터페이스
├── domain/         엔티티, enum, 도메인 규칙, 리포지토리 인터페이스
└── infrastructure/ 리포지토리 구현 외, 포트 구현(어댑터), 외부 연동
```

## 엔티티

- 모두 `common.domain.BaseEntity` 상속: **애플리케이션 생성 UUID**(`assignId()` in 정적 팩터리) + `Persistable`(persist 경로) + `@CreatedDate`/`@LastModifiedDate`.
- 생성자는 `protected`. 생성은 정적 팩터리(`create`/`open`/`of`/`register`)로만. 팩터리에서 불변식 검증.
- 상태 전이는 전용 메서드(`markDocsRequested()` 등). 허용 안 된 전이는 `BusinessException(CONFLICT)`.
- 세터 없음. 값 변경도 의도를 드러내는 메서드(`changePassword`, `applyPlainText`)로.
- `BaseEntity.requireText(value, label)` — 공백이면 `BusinessException(INVALID_INPUT)`, 아니면 trim.

## 에러 처리

- 도메인/유스케이스 위반 → `common.error.BusinessException(ErrorCode, message?)`.
- `ErrorCode`: `INVALID_INPUT(400)`, `UNAUTHORIZED(401)`, `FORBIDDEN(403)`, `NOT_FOUND(404)`, `CONFLICT(409)`, `INTERNAL_ERROR(500)`.
- `common.error.GlobalExceptionHandler`(@RestControllerAdvice) 가 HTTP 로 변환:
  - `BusinessException` → `ErrorCode.status()`
  - `MethodArgumentNotValidException` → 400 `INVALID_INPUT` + 필드 메시지
  - `MissingRequestHeaderException` / `MethodArgumentTypeMismatchException` / `HttpMessageNotReadableException` → 400
  - 그 외 → 500, 원인 미노출

## API 응답 봉투

```json
성공: { "success": true,  "data": <T>,  "error": null }
실패: { "success": false, "data": null, "error": { "code": "NOT_FOUND", "message": "..." } }
```

- `common.web.ApiResponse<T>` — `ApiResponse.ok(data)` / `ApiResponse.ok()` / `ApiResponse.fail(code, msg)`.
- 컨트롤러는 항상 `ApiResponse<...>` 반환. 생성은 `@ResponseStatus(CREATED)` + 응답에 생성된 id.
- 프론트 axios 인터셉터가 봉투를 벗겨 `data` 만 반환하고 `error` 는 throw.

## 행위자 식별

- 컨트롤러가 `@RequestHeader("X-User-Id") UUID userId` 로 받는다 (게이트웨이 주입 전제, 현재는 클라이언트가 직접 전송).
- 역할이 필요한 곳(메시지 senderRole 등)은 당분간 요청 본문에 담는다 (TODO: `X-User-Roles`).
- 권한 검사는 도메인에서: `Case.requireParticipant(userId)` → `FORBIDDEN`, 서비스에서 `command.requesterId().equals(case.getAdjusterId())` 등.

## 검증

- 요청 DTO 에 Jakarta Validation (`@NotBlank`, `@NotNull`, `@Email`, `@Size`, `@NotEmpty`). 컨트롤러 파라미터에 `@Valid`.
- 도메인 규칙 검증은 엔티티/팩터리에서 `BusinessException`.
- 이중 방어 OK (DTO에서 형식, 도메인에서 규칙).

## 영속성

- JSON 은 `common.persistence` 의 `AttributeConverter` 로 `varchar` 에 문자열 저장:
  `JsonMapConverter`(Map<String,Object>), `JsonStringListConverter`(List<String>), `form.infrastructure.FormFieldListConverter`.
  jsonb 이관은 TODO.
- 리포지토리는 Spring Data 파생 쿼리 우선. 정렬은 메서드명에(`findByCaseIdOrderByCreatedAtAsc`).
- `dev` 프로파일: PostgreSQL + Flyway + `ddl-auto: validate`.
- `test` 프로파일: H2(PostgreSQL 호환 모드) + `ddl-auto: create-drop` + Flyway off.

## 보안 설정

- 현재 `common.config.SecurityConfig` = `anyRequest().permitAll()` + CSRF off + STATELESS. (게이트웨이 도입 시 교체)
- 비밀번호: `auth.infrastructure.BCryptPasswordHasher`.
- JWT: RS256, `auth.infrastructure.JwtKeyConfig` 가 부팅마다 RSA 키 생성(TODO: 시크릿에서 로드), `JwtAccessTokenIssuer` 발급/검증, `JwksController` 가 `/.well-known/jwks.json` 공개키 노출.
- 액세스 토큰 15분, refresh 7일(회전 방식).

## 네이밍

- 서비스: `<Verb><Noun>Service` (`OpenCaseService`, `SubmitFormService`).
- 커맨드: `<Verb><Noun>Command`. 읽기 모델: `<Noun>View` 또는 `<Noun>Entry`.
- 포트: 역할 명사(`AiStructurePort`, `StoragePort`). 어댑터: `<Target><Port>Adapter`.
- 테스트: 단위 `XxxTest`, 통합 `XxxIntegrationTest`, JPA 슬라이스 `XxxJpaTest`. `@DisplayName` 한글.

## 프론트 규약

- API 호출은 모두 `/api` 프리픽스 (Vite 프록시가 `:8080` 로 rewrite). 프론트 라우트와 충돌 방지.
- axios 인스턴스(`src/api/client.js`)가 `Authorization: Bearer` + `X-User-Id` 주입, 봉투 unwrap, 401 시 refresh 1회.
- 상태: Pinia. 인증 원천은 `src/api/session.js`(localStorage + JWT 디코드), Pinia 는 반응성 래퍼.
