# 기능: 서류 요청 → 양식 제출

## 관통 모듈

`collab` (DocumentRequest) → `form` (FormTemplate/FormSubmission) → `form.infrastructure` 어댑터 → `collab` (MarkDocumentRequestSubmitted).

## 흐름

```
1. 사정사: 청구 유형 선택 → GET /catalog/life/claim-types/{type}/required-documents
           → [{templateCode:"life.death-certificate", condition:null}, ...]
2. 사정사: 각 서류에 대해 POST /cases/{caseId}/document-requests {templateId, title, dueDate?}
           ⚠️ templateCode → templateId 해석 API 부재. 현재는 GET /templates/{id} 로만 접근.
           → DocumentRequest(PENDING), 케이스 DOCS_REQUESTED
3. 청구인: GET /cases/{caseId}/document-requests → 요청 목록
4. 청구인: GET /templates/{templateId} → 칸 스키마 (FormField[])
5. 청구인: 칸 채워 POST /submissions {documentRequestId, caseId, templateId, data:{key:value,...}}
           - data 에 템플릿에 없는 key → 400 INVALID_INPUT
           - 필수 blank → missingFields 에 담기고 제출은 성립(complete=false)
           - 타입 불일치(NUMBER 에 문자 등) → validationErrors
           - SubmitFormService 가 AiStructurePort.structure 호출 → aiStructured + missingFields 보정 (실패해도 제출 유지)
           - documentRequestId 있으면 → 서류요청 SUBMITTED + 케이스 DOCS_SUBMITTED + 타임라인
6. 사정사: GET /submissions/{submissionId} → data + aiStructured + missingFields + validationErrors + complete
7. 사정사: PATCH /document-requests/{id} {action:"ACCEPT"} 또는 {action:"REJECT", reason:"..."}
           - REJECT 후 청구인이 다시 POST /submissions → version++ , 서류요청 다시 SUBMITTED
```

## 임시저장

- `POST /submissions/draft {documentRequestId, caseId, templateId, data}` → DRAFT 생성/갱신 (검증·전이 없음)
- `PATCH /submissions/{id} {data}` → 기존 제출물 임시 데이터 갱신

## 검증된 결과 (로컬)

`hospital` 만 채우고 `visitDate` 누락 → `status:SUBMITTED, complete:false, missingFields:["visitDate"], aiStructured:{hospital:"..."}`,
서류요청 `SUBMITTED`, 타임라인 마지막 `DOCS_SUBMITTED`.

## 알려진 갭

- `templateCode → templateId` 조회 (`GET /templates?code=`) 필요.
- 케이스별 제출물 목록 조회 없음 (`GET /cases/{id}/submissions`).
- 첨부 파일 연결 미구현 (`document` 모듈, submissionId 로 링크 예정).
- `submit()` 이후 사정사가 UNDER_REVIEW 로 넘길 방법 없음 → 결정까지 못 감.
