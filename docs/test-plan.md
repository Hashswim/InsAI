# InsuranceAI 백엔드 테스트 계획 (TDD)

모든 기능은 **테스트 먼저(RED) → 구현(GREEN) → 리팩터링** 순서로 만든다.
이 문서는 MVP 전 도메인의 테스트 케이스 목록이며, 구현 진행에 따라 각 항목에 `[x]` 표시한다.

## 0. 테스트 계층과 규약

| 계층 | 도구 | 대상 | 비고 |
|---|---|---|---|
| **Domain 단위** | JUnit5 + AssertJ (Spring 없음) | Entity·VO·Enum·도메인 규칙 | 가장 빠름, 가장 많이 |
| **Application 단위** | JUnit5 + Mockito | UseCase 서비스 오케스트레이션·예외 경로 | 포트는 목(mock) |
| **Web 슬라이스** | `@WebMvcTest` + MockMvc | 컨트롤러·검증·응답 형태·에러 엔벨로프·상태코드 | 서비스는 `@MockitoBean` |
| **Persistence 슬라이스** | `@DataJpaTest` + H2(PostgreSQL 호환) | Repository 쿼리·제약·매핑 | `test` 프로파일, `AbstractJpaTest` |
| **통합** | `@SpringBootTest(MOCK)` + MockMvc + H2 | HTTP 진입 → DB 까지 전 계층 플로우 | `test` 프로파일, `AbstractIntegrationTest` |

> DB 참고: 이 머신의 Docker Desktop 과 Testcontainers 가 통신되지 않아(`/info` 400) Persistence/통합
> 테스트를 H2(PostgreSQL 호환 모드)로 돌린다. Testcontainers 재도입은 `build.gradle` / `AbstractJpaTest`
> / `AbstractIntegrationTest` 의 TODO 로 보존.

규약
- 클래스명: 단위 `XxxTest`, 통합 `XxxIntegrationTest`, JPA 슬라이스 `XxxJpaTest`
- 메서드명: 한글 서술 (`@DisplayName` 또는 메서드명), given/when/then 주석
- 하나의 테스트 = 하나의 행위/기대
- 단정: **AssertJ** (`assertThat`), 예외는 `assertThatThrownBy`
- 픽스처: `support/fixture/*Fixture` 빌더로만 생성, 테스트 본문에 new 남발 금지
- Testcontainers: PostgreSQL/Redis 컨테이너는 **싱글턴 재사용** (`support/PostgresContainerConfig`)
- `it` 프로파일: 스키마는 `ddl-auto: create-drop` 로 엔티티가 주도 (Flyway 마이그레이션 정합성은 별도 검증 단계에서 추가)

## 공통 (common)

- [ ] `ApiResponse` 직렬화: 성공 `{success:true,data,error:null}`, 실패 `{success:false,data:null,error:{code,message}}`
- [ ] `GlobalExceptionHandler`: `BusinessException` → `ErrorCode.status()` + `error.code=ErrorCode.name()`
- [ ] `GlobalExceptionHandler`: `MethodArgumentNotValidException` → 400 + `error.code=INVALID_INPUT` + 필드 메시지 결합
- [ ] `GlobalExceptionHandler`: 미처리 예외 → 500 + `INTERNAL_ERROR` (원인 메시지 노출 안 함)
- [ ] `GET /api/ping` → 200 `{success:true,data:{service:"insai",time:...}}`
- [ ] `GET /actuator/health` → 200 `{"status":"UP"}`

---

## 1. auth (인증·회원)

### 1.1 Domain — `Account`, `Role`, `AccountStatus`
- [ ] `Account.create`: 이메일을 소문자·trim 정규화해 저장
- [ ] `Account.create`: passwordHash 로만 저장, 평문 비밀번호 필드 없음
- [ ] `Account.create`: role 미지정 시 기본 `CLAIMANT`, 상태 `ACTIVE`
- [ ] `Account.create(role=ADJUSTER)`: 상태 `PENDING` (관리자 승인 대기)
- [ ] `Account.activate`: `PENDING` → `ACTIVE`
- [ ] `Account.suspend`: `ACTIVE` → `SUSPENDED`
- [ ] `Account.changePassword`: 새 해시로 교체, 기존과 달라야 함
- [ ] `Account.verifyActive`: `PENDING`/`SUSPENDED` 이면 `BusinessException(FORBIDDEN)`
- [ ] `Role`: 권한 문자열 매핑(`ROLE_CLAIMANT` 등)

### 1.2 Application
- [ ] `SignupService`: 정상 → 저장, `PasswordHasher` 로 인코딩된 해시 저장, id 반환
- [ ] `SignupService`: 이미 존재하는 이메일 → `BusinessException(CONFLICT)`, 저장 호출 안 함
- [ ] `SignupService`: role=ADJUSTER → `PENDING` 상태로 생성
- [ ] `LoginService`: 정상 → `TokenPair(access, refresh)`, `RefreshTokenStore.save` 호출
- [ ] `LoginService`: 미존재 이메일 → `BusinessException(UNAUTHORIZED)` (계정 존재 여부 비노출 메시지)
- [ ] `LoginService`: 비밀번호 불일치 → `BusinessException(UNAUTHORIZED)`
- [ ] `LoginService`: `SUSPENDED`/`PENDING` 계정 → `BusinessException(FORBIDDEN)`
- [ ] `TokenRefreshService`: 유효 refresh → 새 `TokenPair`, 기존 refresh 삭제(회전)
- [ ] `TokenRefreshService`: 미존재/만료 refresh → `BusinessException(UNAUTHORIZED)`
- [ ] `TokenRefreshService`: 이미 회전된 refresh 재사용 → `UNAUTHORIZED`
- [ ] `LogoutService`: refresh 삭제, 존재하지 않아도 성공(멱등)

### 1.3 Infrastructure — `JwtAccessTokenIssuer` (RS256)
- [ ] 발급 토큰 클레임: `sub=accountId`, `roles`, `iss`, `iat`, `exp`
- [ ] 만료 = 발급 시각 + 15분
- [ ] 유효 토큰 → `parse` 성공, accountId/roles 복원
- [ ] 만료된 토큰 → 검증 실패 예외
- [ ] 서명 변조 토큰 → 검증 실패
- [ ] 다른 키로 서명된 토큰 → 검증 실패
- [ ] `JwksController` `GET /.well-known/jwks.json` → JWK Set, `kid` 포함, 개인키 성분(`d`) 미포함

### 1.4 Web — `@WebMvcTest(AuthController)`
- [ ] `POST /auth/signup` 유효 → 201, `{success:true}`
- [ ] `POST /auth/signup` 이메일 형식 오류 → 400 `INVALID_INPUT`
- [ ] `POST /auth/signup` 비밀번호 8자 미만 → 400
- [ ] `POST /auth/signup` 이름 공백 → 400
- [ ] `POST /auth/signup` 서비스가 CONFLICT → 409
- [ ] `POST /auth/login` 유효 → 200 `{data:{accessToken,refreshToken,expiresIn}}`
- [ ] `POST /auth/login` 잘못된 자격 → 401
- [ ] `POST /auth/refresh` 유효 → 200 새 토큰 / 무효 → 401
- [ ] `POST /auth/logout` → 200

### 1.5 Persistence — `@DataJpaTest AccountRepository`
- [ ] `save` 후 `findByEmail` 로 조회
- [ ] `existsByEmail` true/false
- [ ] 이메일 unique 제약 위반 → `DataIntegrityViolationException`
- [ ] `createdAt`/`updatedAt` 자동 세팅 (JPA Auditing)

### 1.6 통합 — `AuthFlowIntegrationTest`
- [ ] 가입 → 로그인 → accessToken 으로 `/api/ping`(보호 전환 후) 접근 → refresh → logout → refresh 재시도 401
- [ ] 잘못된 비밀번호 로그인 → 401
- [ ] ADJUSTER 가입 후 승인 전 로그인 → 403

---

## 2. collab (청구 협업 공간 코어)

### 2.1 Domain — `Case`, `Participant`, `DocumentRequest`, `Message`, `Decision`, `TimelineEvent`
- [ ] `Case.open(claimantId)`: 상태 `OPEN`, 청구인 `Participant` 등록, 타임라인 `CASE_OPENED`
- [ ] `Case.assignAdjuster(adjusterId)`: 사정사 `Participant` 추가, 타임라인 `ADJUSTER_ASSIGNED`
- [ ] `Case.assignAdjuster` 이미 배정됨 → `BusinessException(CONFLICT)` (재배정은 별도 명령)
- [ ] `Case` 상태 전이 정상: `OPEN→DOCS_REQUESTED→DOCS_SUBMITTED→UNDER_REVIEW→DECISION_MADE→CLOSED`
- [ ] `Case` 잘못된 전이: `CLOSED` 에서 서류 요청/메시지/결정 → `BusinessException(CONFLICT)`
- [ ] `Case.requireParticipant(userId)`: 비참여자 → `BusinessException(FORBIDDEN)`
- [ ] `DocumentRequest.create`: 상태 `PENDING`, templateId 참조, dueDate 옵션
- [ ] `DocumentRequest.markSubmitted`: `PENDING`→`SUBMITTED`
- [ ] `DocumentRequest.accept` / `reject(reason)`: `SUBMITTED`→`ACCEPTED`/`REJECTED`, reject 는 사유 필수
- [ ] `DocumentRequest.reject` 후 재제출 → `SUBMITTED` 로 복귀 허용
- [ ] `Message.of`: 발신자 역할 기록, `aiMediated` 플래그
- [ ] `Decision.of`: rawText 필수(공백 불가), plainText 는 null 허용(AI 후처리 전)

### 2.2 Application
- [ ] `OpenCaseService`: 청구인 요청 → `Case` 저장, id 반환
- [ ] `AssignAdjusterService`: ADMIN → 배정, 이미 배정된 케이스 → `CONFLICT`
- [ ] `CreateDocumentRequestService`: 배정된 ADJUSTER 만, 타 사용자 → `FORBIDDEN`
- [ ] `CreateDocumentRequestService`: `CLOSED` 케이스 → `CONFLICT`
- [ ] `CreateDocumentRequestService`: 성공 시 케이스 상태 `DOCS_REQUESTED`, 타임라인 기록
- [ ] `UpdateDocumentRequestStatusService`: accept/reject, reject 사유 없음 → `INVALID_INPUT`
- [ ] `PostMessageService`: 케이스 참여자만, 비참여자 → `FORBIDDEN`
- [ ] `RegisterDecisionService`: 배정 ADJUSTER 만, 케이스 `UNDER_REVIEW` 에서만 → `DECISION_MADE` 전이
- [ ] `GetCaseTimelineService`: 이벤트 시간 오름차순 반환
- [ ] `MarkDocsSubmittedService`(form 도메인에서 호출되는 포트): DocumentRequest `SUBMITTED` + 케이스 `DOCS_SUBMITTED`

### 2.3 Web — `@WebMvcTest`
- [ ] `POST /cases` (CLAIMANT) → 201
- [ ] `PATCH /cases/{id}/assign` (ADMIN) → 200 / (그 외 역할) → 403
- [ ] `POST /cases/{id}/document-requests` (ADJUSTER) → 201 / (CLAIMANT) → 403
- [ ] `GET /cases/{id}/document-requests` → 200 목록
- [ ] `PATCH /document-requests/{id}` accept/reject → 200
- [ ] `POST /cases/{id}/messages` → 201
- [ ] `POST /cases/{id}/decisions` (ADJUSTER) → 201
- [ ] `GET /cases/{id}/timeline` → 200 시간순
- [ ] 없는 케이스 → 404, 비참여자 접근 → 403

### 2.4 Persistence — `@DataJpaTest`
- [ ] `CaseRepository`: `findByClaimantId`, `findByAdjusterId`, `findByStatus`
- [ ] `DocumentRequestRepository`: `findByCaseId`, `findByCaseIdAndStatus`
- [ ] `MessageRepository`: `findByCaseIdOrderByCreatedAtAsc`
- [ ] `TimelineEventRepository`: `findByCaseIdOrderByCreatedAtAsc`
- [ ] `Case` ↔ `Participant`/`DocumentRequest` cascade 및 orphanRemoval 매핑

### 2.5 통합 — `CaseCollaborationIntegrationTest`
- [ ] 배정된 케이스: 서류요청 생성 → 청구인 목록 조회 → markSubmitted → 결정 등록 → 타임라인에 4개 이벤트 순서대로
- [ ] 청구인이 서류요청 생성 시도 → 403
- [ ] `CLOSED` 케이스에 메시지 → 409

---

## 3. form (양식 템플릿 · 제출물)

### 3.1 Domain — `FormTemplate`, `FormField`, `FormSubmission`
- [ ] `FormTemplate.create`: 필드 0개 → `INVALID_INPUT`
- [ ] `FormTemplate.create`: 중복 필드 key → `INVALID_INPUT`
- [ ] `FormField` 검증규칙: text `maxLength`, number `min/max`, date 형식, select `options` 내 값, file
- [ ] `FormSubmission.draft`: 상태 `DRAFT`, 부분 데이터 허용
- [ ] `FormSubmission.submit`: 템플릿에 없는 key 포함 → `INVALID_INPUT`
- [ ] `FormSubmission.submit`: 값 타입 불일치 → `validationErrors` 에 기록
- [ ] `FormSubmission.submit`: 필수 필드 누락 → 제출은 되지만 `missingFields` 채워지고 `complete=false`
- [ ] `FormSubmission.submit` 재제출: `version` 1 증가
- [ ] `FormSubmission.applyAiStructured`: AI 구조화 결과 병합, 사용자 입력값 우선

### 3.2 Application
- [ ] `CreateTemplateService` (ADMIN): 스키마 검증 통과 시 저장
- [ ] `GetTemplateService`: 렌더용 스키마(JSON) 반환
- [ ] `SaveDraftService`: 부분 저장, 상태 유지 `DRAFT`
- [ ] `SubmitFormService`: `AiStructurePort` 호출 → `aiStructured` + `missingFields` 저장
- [ ] `SubmitFormService`: 성공 시 `MarkDocsSubmittedPort`(collab) 호출
- [ ] `SubmitFormService`: `AiStructurePort` 실패해도 원본 data 로 제출은 성공(구조화는 나중 재시도)
- [ ] `GetSubmissionService` (ADJUSTER): data + aiStructured + missingFields 반환

### 3.3 Web — `@WebMvcTest`
- [ ] `POST /templates` (ADMIN) → 201 / 스키마 오류 → 400
- [ ] `GET /templates/{id}` → 200 스키마 / 없음 → 404
- [ ] `POST /submissions` → 201
- [ ] `PATCH /submissions/{id}` (draft) → 200
- [ ] `GET /submissions/{id}` → 200
- [ ] 템플릿 미정의 필드 포함 제출 → 400

### 3.4 Persistence — `@DataJpaTest`
- [ ] `FormTemplateRepository`: `findByCode`, 최신 `version`
- [ ] `FormSubmissionRepository`: `findByDocumentRequestId`, `findByCaseId`
- [ ] `data`/`aiStructured` JSON(jsonb) 컬럼 매핑 왕복
- [ ] `missingFields` 리스트 매핑

### 3.5 통합 — `FormSubmissionIntegrationTest`
- [ ] 템플릿 등록 → 자유입력 포함 제출 → `FakeAiClient` 구조화 결과가 `aiStructured` 에 저장 → 사정사 조회 시 노출
- [ ] 필수 3개 중 1개 누락 제출 → `missingFields` 정확히 1개

---

## 4. document (파일 저장) — GREEN

### 4.1 Domain — `StoredFile` (`StoredFileTest`)
- [x] `StoredFile.register`: storageKey/contentType/size/uploaderId 필수, isNew
- [x] 허용 확장자 외 → `INVALID_INPUT`
- [x] 용량 0 이하 / 상한(20MB) 초과 → `INVALID_INPUT`
- [x] `extensionOf`: 마지막 점 뒤 소문자 / 점 없으면 빈 문자열
- [x] `attachOcrText`: OCR 텍스트 부착

### 4.2 Application (`DocumentFileServicesTest`, `CaseAccessServiceTest`, `LocalDiskStorageTest`)
- [x] `IssueUploadUrlService`: 참여자 확인 → `StoragePort.uploadUrl` → `{url, storageKey}` (key = `caseId/uuid.ext`); 비참여자 → `FORBIDDEN`; 나쁜 확장자 → `INVALID_INPUT`
- [x] `ConfirmUploadService`: 참여자 확인 → `StoredFile` 저장 + OCR 텍스트 부착; OCR 실패해도 저장
- [x] `IssueDownloadUrlService`: 없는 파일 → `NOT_FOUND`, 비참여자 → `FORBIDDEN`, 참여자 → `{url}`
- [x] `ListCaseFilesService`: `byCase` / `bySubmission` → 뷰 매핑
- [x] `collab.CaseAccessService`: 없는 케이스 → `NOT_FOUND`, 참여자 통과 / 그 외 `FORBIDDEN`
- [x] `LocalDiskStorage`: write→read 라운드트립, `..` 경로 탈출 차단, 없는 키 → `NOT_FOUND`

### 4.3 Web — `@WebMvcTest` (`FileControllerTest`)
- [x] `POST /files/upload-url` → 200 `{url, storageKey}`; X-User-Id 없으면 400; filename 없으면 400
- [x] `POST /files` (업로드 확인) → 201 `{fileId}`
- [x] `GET /files/{id}/download-url` → 200 `{url}` / 비참여자 → 403
- [x] `GET /files?caseId=` → 200 리스트; caseId/submissionId 둘 다 없으면 400

### 4.4 Persistence — `@DataJpaTest` (`StoredFileRepositoryJpaTest`)
- [x] `findByCaseIdOrderByCreatedAtAsc`, `findBySubmissionIdOrderByCreatedAtAsc`

### 4.5 수동 E2E (curl, dev)
- [x] upload-url 발급 → `/local-storage` PUT → confirm → `?caseId=` 조회 → download-url → 바이트 다운로드 (직접 + Vite 프록시 경유)
- [x] 다른 사용자가 download-url 요청 → 403; 나쁜 확장자 업로드 → INVALID_INPUT

---

## 5. ai (LLM 오케스트레이션)

포트 `LlmClient` 는 테스트에서 `FakeLlmClient` 로 대체. 스텁 구현이라도 **응답 계약(스키마)** 은 테스트로 고정한다.

### 5.1 Application
- [ ] `StructureService`: 자유입력 + 템플릿 스키마 → `{fields:{key:value}, confidence:{key:0~1}, missingFields:[]}`
- [ ] `StructureService`: 스키마의 모든 필수 key 가 결과 fields 또는 missingFields 중 하나에 존재
- [ ] `ReviewSummaryService`: 제출 data + OCR 텍스트 → `{summary, highlights:[], issues:[], needsAttention:boolean}`
- [ ] `ReviewSummaryService`: 누락/불일치 항목이 `issues` 에 반영
- [ ] `LlmClient` 타임아웃 → `BusinessException(INTERNAL_ERROR)` 로 변환 (원문 예외 누출 안 함)
- [ ] `LlmClient` 프로바이더 교체 가능 (FakeLlmClient 주입만으로 동작)

### 5.2 Web — `@WebMvcTest`
- [ ] `POST /ai/structure` 유효 → 200 계약 스키마 준수
- [ ] `POST /ai/review-summary` 유효 → 200 계약 스키마 준수
- [ ] 입력 누락 → 400
- [ ] LLM 실패 → 502 + 에러 엔벨로프

### 5.3 통합 — `AiOrchestrationIntegrationTest`
- [ ] `FakeLlmClient` 주입 → `/ai/structure` end-to-end 응답 형태 보장
- [ ] `/ai/review-summary` end-to-end 응답 형태 보장

### 5.4 (2차)
- [ ] `TranslateDecisionService`: 사정 결과 원문 + 약관 조항 → 평문 + 근거 인용
- [ ] `AnswerService`: 질문 + 약관 검색결과 → 1차 응답 + `needsAdjusterReview` 플래그

---

## 6. policy (가입 보험 조회) — GREEN

### 6.1 Domain — `Policy` (`PolicyTest`)
- [x] `register`: 필수값 채우면 생성, isNew, coverageEndDate 는 null 허용(종신)
- [x] 보험사/상품명/증권번호/피보험자 공백 → `INVALID_INPUT`

### 6.2 Application (`PolicyServicesTest`)
- [x] `ListMyPoliciesService.list`: 계약 → 뷰(planTypeLabel/statusLabel 포함)
- [x] `SeedDemoPoliciesService.seedFor`: 계약 없으면 데모 3건 저장(피보험자명 반영)
- [x] 이미 계약이 있으면 no-op / `ownerId==null` 이면 조회도 안 함

### 6.3 Web — `@WebMvcTest` (`PolicyControllerTest`)
- [x] `GET /policies` → 200 목록; X-User-Id 없으면 400

### 6.4 Persistence — `@DataJpaTest` (`PolicyRepositoryJpaTest`)
- [x] `findByOwnerIdOrderByContractDateDesc`(본인 것만·계약일 내림차순), `existsByOwnerId`

### 6.5 수동 E2E (curl, dev)
- [x] `dev/demo-login`(CLAIMANT) → 데모 3건 자동 시드 → `GET /policies` 조회 (직접 + Vite 프록시)
- [x] `POST /dev/seed-policies` idempotent (재호출해도 3건 유지)

---

## 진행 현황

| 도메인 | 상태 |
|---|---|
| common | 스켈레톤 존재, 전용 테스트 미작성 |
| **auth** | ✅ GREEN (44/44) — domain·application·infra·web·persistence·통합 |
| **collab** | ✅ GREEN — domain · application · web · persistence · 통합 (+ `MarkDocumentRequestSubmittedService` form 연동점) |
| **form** | ✅ GREEN — domain · application · web · persistence · 통합. 제출 성립 시 collab DOCS_SUBMITTED 전이 |
| **form.catalog (생명보험)** | ✅ GREEN — 청구 서류 15종 FormTemplate 정의(공통 4 + 사망/장해/진단/입원·수술), 유형별 요구 서류 매트릭스, 부팅 시 멱등 시드, `GET /catalog/life/*` |
| document | ✅ GREEN — `StoredFile` + 2단계 업로드(upload-url→PUT→confirm) + 참여자 제한 다운로드 + `LocalDiskStorage`/`StubOcrPort` + `V4__document.sql` + 프론트 FILE 필드 실동작 |
| ai | ✅ GREEN — `LlmClient` 포트(+`StubLlmClient`/`AnthropicLlmClient`), `ReviewSummaryService`, `LlmAiStructurePort`(@Primary), `LlmDecisionTranslator`. `insai.ai.enabled` 로 스텁↔실 Claude 토글 |
| policy | ✅ GREEN — `Policy` + `ListMyPoliciesService` + `SeedDemoPoliciesService` + `V5__policy.sql` + `GET /policies`. 홈 "내 보험 조회하기" → `PoliciesView` |

### 다음 순서
1. 실제 OCR / 오브젝트 스토리지 presigner (현재 로컬 디스크)
2. `INSAI_AI_API_KEY` 수령 후 `AnthropicLlmClient` 실호출 검증
3. policy: 보험사 연동 또는 청구인 직접 등록 · 청구 케이스에서 증권 선택
