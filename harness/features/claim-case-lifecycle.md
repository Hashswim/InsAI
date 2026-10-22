# 기능: 청구 케이스 라이프사이클

## 관통 모듈

`collab` (코어) · `form` (제출이 상태 전이 트리거) · 프론트 CaseDetailView.

## 상태 머신

```
OPEN ──(사정사 배정)──> (adjuster 세팅, 상태 유지)
  │
  ├─(사정사: 서류 요청 생성)──> DOCS_REQUESTED
  │                                │
  │            (청구인: 양식 제출 성립)  ▼
  │                              DOCS_SUBMITTED
  │                                │
  │              (사정사: 검토 착수 — ⚠️엔드포인트 없음) ▼
  │                              UNDER_REVIEW
  │                                │
  │                (사정사: 결정 등록)  ▼
  │                              DECISION_MADE
  │                                │
  │                     (사정사: 종료)  ▼
  │                                CLOSED
  │
  └─ 추가 서류 필요 시 DOCS_SUBMITTED/UNDER_REVIEW → DOCS_REQUESTED 로 회귀 가능
```

허용 안 된 전이 → `BusinessException(CONFLICT)`.

## 타임라인 이벤트 (감사 + UI 피드)

`CASE_OPENED → ADJUSTER_ASSIGNED → DOCS_REQUESTED → DOCS_SUBMITTED → REVIEW_STARTED → DECISION_MADE → CASE_CLOSED`, 그리고 `MESSAGE_POSTED` (수시).
`GET /cases/{id}/timeline` — 시간 오름차순.

## 권한

| 행위 | 누구 | 위반 시 |
|---|---|---|
| 케이스 개설 | 청구인 | — |
| 사정사 배정 | (현재) body 로 adjusterId — ADMIN 성격 | 이미 배정 → 409 |
| 서류 요청 생성 | **배정된 사정사만** | 403 / CLOSED → 409 |
| 서류요청 accept/reject | 사정사 | reject 사유 없음 → 400 |
| 메시지 작성 | **케이스 참여자** (청구인·배정사정사) | 비참여자 → 403 |
| 결정 등록 | **배정된 사정사** + 케이스 UNDER_REVIEW | 403 / 상태 아님 → 409 |

## 검증된 흐름 (로컬 curl, `CaseCollaborationIntegrationTest`)

```
POST /cases (X-User-Id=청구인)                → caseId
PATCH /cases/{caseId}/assign {adjusterId}     → 200
POST /cases/{caseId}/document-requests (X-User-Id=사정사) {templateId,title}  → docReqId
GET  /cases/{caseId}/document-requests        → [{status:"PENDING"}]
POST /submissions (X-User-Id=청구인) {documentRequestId,caseId,templateId,data}
     → 서류요청 SUBMITTED, 케이스 DOCS_SUBMITTED, 타임라인에 DOCS_SUBMITTED
POST /cases/{caseId}/messages {senderRole,body}   (참여자 201 / 비참여자 403)
GET  /cases/{caseId}/timeline                 → [CASE_OPENED, ADJUSTER_ASSIGNED, DOCS_REQUESTED, DOCS_SUBMITTED, MESSAGE_POSTED]
```

## 알려진 갭

- **UNDER_REVIEW 로 가는 엔드포인트 없음** → `RegisterDecisionService` 가 UNDER_REVIEW 를 요구하므로 현재 결정 등록 불가. `POST /cases/{id}/review` (startReview) 필요.
- `GET /cases`(목록)·`GET /cases/{id}`(상세) 없음.
- 메시지 스레드 조회 API 없음 (작성만). 타임라인의 MESSAGE_POSTED 로 개수만 파악 가능 → `GET /cases/{id}/messages` 추가 필요.
