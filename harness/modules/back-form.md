# 모듈: back/insai · com.insuranceai.form

## 목적

서류 양식 템플릿(칸 구조)과 청구인 제출물. 제출 시 검증·누락 계산·AI 구조화, 제출 성립 시 collab 상태 전이 트리거.
**책임 밖**: 케이스 상태 머신(collab), 실제 LLM(ai), 파일 바이트(document).

## 경로

- `back/insai/src/main/java/com/insuranceai/form/`  (하위 `catalog/` 는 별도 문서)
- 테스트: `back/insai/src/test/java/com/insuranceai/form/`
- 스키마: `V2__form.sql` — `form_templates`, `form_submissions`

## 핵심 타입

| 도메인 | 규칙 |
|---|---|
| `FormTemplate` | code + version + `List<FormField>`(JSON). `create` 시 필드 0개/중복 key → INVALID_INPUT. `fieldKeys()` |
| `FormField` (record) | key, label, `FieldType`, required, options, maxLength, min, max. `validate(value)` → 사유 or null |
| `FieldType` | TEXT(maxLength) / NUMBER(min·max) / DATE(YYYY-MM-DD) / SELECT(options) / FILE |
| `FormSubmission` | data/aiStructured(Map JSON), missingFields/validationErrors(List JSON), status, version, complete |
| `SubmissionStatus` | DRAFT / SUBMITTED |

`FormSubmission.submit(template, data)`:
- 템플릿에 없는 key → **INVALID_INPUT (throw)**
- 필수인데 blank → `missingFields` 에 추가, 상태는 SUBMITTED, `complete=false` (제출은 성립)
- 타입 불일치 → `validationErrors`
- 재제출마다 `version++`
`applyAiStructured(aiFields, aiMissing)`: AI 가 채운 누락 필드는 missingFields 에서 제거, `aiStructured` 저장.

| 서비스 | 요약 |
|---|---|
| `CreateTemplateService` | 같은 code 있으면 version+1 |
| `GetTemplateService` | 렌더용 `FormTemplateView`(필드 포함), 없으면 NOT_FOUND |
| `SaveDraftService` | `saveDraft(cmd)` (docReqId 기준 생성/갱신) + `updateDraft(id, data)` |
| `SubmitFormService` | 템플릿 로드(NOT_FOUND) → submit → `AiStructurePort.structure`(실패해도 계속) → 저장 → docReqId 있으면 `DocumentRequestSubmissionPort.onFormSubmitted` |
| `GetSubmissionService` | 사정사 검토용 `FormSubmissionView` |
| `AiStructurePort` (포트) | `structure(rawInput, template)` → `{fields, confidence, missingFields}` |
| `DocumentRequestSubmissionPort` (포트) | `onFormSubmitted(docReqId, submittedBy)` → collab |
| `infrastructure.RuleBasedAiStructurePort` | 스텁: 입력 에코 + 필수 누락 계산 (ai 도메인이 실물로 교체 예정) |
| `infrastructure.CollabDocumentRequestSubmissionAdapter` | `MarkDocumentRequestSubmittedService` 로 위임 |

| API | |
|---|---|
| `POST /templates` | 템플릿 등록 (ADMIN 성격), 스키마 오류 400 |
| `GET /templates/{id}` | 렌더용 스키마 |
| `POST /submissions` | 제출 (X-User-Id). 정의 안 된 필드 → 400 |
| `POST /submissions/draft` | 임시저장 생성/갱신 (docReqId 기준) |
| `PATCH /submissions/{id}` | 임시 데이터 갱신 |
| `GET /submissions/{id}` | 제출물 조회 |

## 의존 관계

- 들어옴: `catalog` 가 `FormTemplate`/`FormField` 사용. (프론트가 REST)
- 나감: `common`, `collab.application.MarkDocumentRequestSubmittedService`(어댑터), `auth.domain.Role`(catalog 아님).

## 테스트

`./gradlew test --tests "com.insuranceai.form.*"` — 도메인 3 + 앱 3(서비스별) + 웹 2 + `FormRepositoryJpaTest` + `FormSubmissionIntegrationTest`(템플릿→케이스→서류요청→제출→DOCS_SUBMITTED).

## 주의 (gotchas)

- JSON 컬럼은 varchar (8000/2000). 큰 템플릿/데이터는 넘칠 수 있음 → jsonb TODO.
- `SubmitFormService` 는 `@Transactional` — `DocumentRequestSubmissionPort` 가 CONFLICT(케이스 상태 등) 던지면 제출 전체 롤백.
- `submit()` 의 필수 누락은 예외가 아니라 `missingFields` — "제출은 되지만 미완" 정책.

## 상태·TODO

- [x] 전 계층 GREEN, 로컬 구동 검증
- [ ] `AiStructurePort` 실물화 (ai 모듈)
- [ ] 제출물 목록 조회(케이스별), 첨부 파일 연결(document)
- [ ] jsonb
