# 모듈: back/insai · com.insuranceai.collab

## 목적

청구 협업 공간(케이스)의 코어. 케이스 상태 머신, 서류 요청, 메시지, 사정 결과, 타임라인.
**책임 밖**: 양식 스키마/제출물(form), 파일(document), AI 텍스트 생성(ai).

## 경로

- `back/insai/src/main/java/com/insuranceai/collab/`
- 테스트: `back/insai/src/test/java/com/insuranceai/collab/`
- 스키마: `V1__init.sql` — `cases`, `document_requests`, `messages`, `decisions`, `timeline_events`

## 핵심 타입

| 도메인 | 규칙 |
|---|---|
| `Case` | claimantId(필수), adjusterId(배정 후), title, `CaseStatus` |
| `CaseStatus` | OPEN → DOCS_REQUESTED → DOCS_SUBMITTED → UNDER_REVIEW → DECISION_MADE → CLOSED |
| `DocumentRequest` | `DocumentRequestStatus` PENDING → SUBMITTED → ACCEPTED/REJECTED (REJECTED 후 재제출) |
| `Message` | senderId, senderRole(`Role`), body, aiMediated |
| `Decision` | adjusterId, rawText(필수) |
| `TimelineEvent` | `TimelineEventType` (CASE_OPENED, ADJUSTER_ASSIGNED, DOCS_REQUESTED, DOCS_SUBMITTED, REVIEW_STARTED, DECISION_MADE, CASE_CLOSED, MESSAGE_POSTED) |

| 서비스 | 요약 |
|---|---|
| `OpenCaseService` | 케이스 생성 + CASE_OPENED |
| `AssignAdjusterService` | 배정, 이미 배정 시 CONFLICT + ADJUSTER_ASSIGNED |
| `CreateDocumentRequestService` | 배정 사정사만(FORBIDDEN), CLOSED 시 CONFLICT, 케이스→DOCS_REQUESTED |
| `ListCaseDocumentRequestsService` | 케이스별 서류요청 뷰 목록 |
| `UpdateDocumentRequestStatusService` | ACCEPT/REJECT, reject 사유 없으면 INVALID_INPUT |
| `PostMessageService` | 참여자만(FORBIDDEN) + MESSAGE_POSTED |
| `RegisterDecisionService` | 배정 사정사 + UNDER_REVIEW 에서만, 케이스→DECISION_MADE |
| `GetCaseTimelineService` | 시간 오름차순 |
| `MarkDocumentRequestSubmittedService` | **form 연동점** — 서류요청 SUBMITTED + 케이스 DOCS_SUBMITTED + DOCS_SUBMITTED 이벤트 |

| API (`api.CaseController`) | |
|---|---|
| `POST /cases` | 개설 (X-User-Id=청구인) |
| `PATCH /cases/{id}/assign` | 사정사 배정 (body: adjusterId) |
| `POST /cases/{id}/document-requests` | 서류 요청 (X-User-Id=사정사, body: templateId,title,dueDate?) |
| `GET /cases/{id}/document-requests` | 목록 |
| `PATCH /document-requests/{id}` | accept/reject (body: action, reason?) |
| `POST /cases/{id}/messages` | 메시지 (body: senderRole, body) |
| `POST /cases/{id}/decisions` | 결정 등록 (body: rawText) |
| `GET /cases/{id}/timeline` | 타임라인 |

## 불변식·규칙

- 상태 전이는 `Case` 전용 메서드로만. 허용 안 된 전이 → CONFLICT.
- `markDocsRequested()` 허용 상태: OPEN / DOCS_SUBMITTED / UNDER_REVIEW (추가 요청 루프).
- `requireParticipant(userId)`: claimantId·adjusterId 아니면 FORBIDDEN.
- 각 유스케이스 = 케이스 로드(NOT_FOUND) → 권한/상태 검증 → 도메인 변경 → 저장 → 타임라인.

## 의존 관계

- 들어옴: `form` → `MarkDocumentRequestSubmittedService` (form.infrastructure 어댑터 경유).
- 나감: `common`, `auth.domain.Role`.

## 테스트

`./gradlew test --tests "com.insuranceai.collab.*"` — 도메인 3클래스 + 서비스 8클래스 + `CaseControllerTest`(@WebMvcTest) + `CollabRepositoryJpaTest` + `CaseCollaborationIntegrationTest`.

## 주의 (gotchas)

- 엔티티 클래스명 `Case` 는 SQL 예약어 `case` 와 다름(복수 테이블명 `cases`) — OK. JPQL 파생 쿼리는 리포지토리 제네릭으로 동작.
- 타임라인 순서 테스트는 `createdAt` 동시성 대비 `Thread.sleep(5)` 사이에 둠.
- **`GET /cases`(목록), `GET /cases/{id}`(상세) 엔드포인트 없음** → 프론트가 필요. `CaseView` record 는 추가됨(미완).

## 상태·TODO

- [x] 도메인·애플리케이션·웹·persistence·통합 GREEN
- [ ] `GET /cases`, `GET /cases/{id}` (프론트 블로커) — `ListCasesService`/`GetCaseService`
- [ ] `startReview()` 를 트리거하는 엔드포인트(사정사가 검토 착수) — 현재 DOCS_SUBMITTED→UNDER_REVIEW 경로 노출 안 됨
- [ ] 메시지 CLOSED 케이스 차단(통합 시나리오에 있으나 미구현)
