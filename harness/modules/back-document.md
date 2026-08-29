# 모듈: back/insai · com.insuranceai.document  (예정 — 미착수)

## 목적

첨부 파일 저장. Presigned URL 발급, 업로드 확인, 다운로드(케이스 참여자만), OCR 텍스트 부착.
**책임 밖**: 파일 내용 해석/요약(ai), 양식 값(form).

## 경로 (계획)

- `back/insai/src/main/java/com/insuranceai/document/`
- 스키마: `V3__document.sql` (예정) — `stored_files`

## 설계 (test-plan.md §4 기준)

| 도메인 | |
|---|---|
| `StoredFile` (BaseEntity) | caseId, submissionId?, uploaderId, filename, contentType, sizeBytes, storageKey(unique), ocrText? |
| `StoredFile.register(...)` | 허용 확장자(pdf/jpg/png/heic/tif/webp) 외 → INVALID_INPUT, 용량 상한(20MB) 초과 → INVALID_INPUT |
| `StoredFile.validateUploadable(filename, size)` | 업로드 전 검증에도 재사용 |
| `StoredFile.attachOcrText(text)` | |

| 서비스·포트 | |
|---|---|
| `IssueUploadUrlService` | `StoragePort.uploadUrl(key)` → `{url, storageKey}`. key = `caseId/uuid.ext` |
| `ConfirmUploadService` | `StoredFile.register` + 저장 |
| `IssueDownloadUrlService` | `CaseParticipantChecker.requireParticipant(caseId, userId)` (collab) → `StoragePort.downloadUrl` |
| `AttachOcrTextService` | `OcrPort.extractText(storageKey)` → `attachOcrText` |
| `StoragePort` (포트) | `uploadUrl(key)` / `downloadUrl(key)` — 구현: MinIO/S3 (현재 `StubStoragePort` 예정) |
| `OcrPort` (포트) | `extractText(storageKey)` — 구현: Tesseract/클라우드 비전 (현재 스텁, 나중에 ai 위임) |

| API | |
|---|---|
| `POST /files/upload-url` | `{url, storageKey}` |
| `POST /files` | 업로드 확인 → 201 `{fileId}` |
| `GET /files/{id}/download-url` | 참여자 → `{url}`, 비참여자 → 403 |

## 의존 관계 (계획)

- 나감: `common`, `collab.application` (참여자 확인 포트). `form` 은 submissionId 로 느슨히 연결.
- 필요: collab 에 `CaseAccessService.requireParticipant(caseId, userId)` 추가.

## 주의 (예상 gotchas)

- Presigned URL 실제 발급은 MinIO/S3 자격증명 필요 → 초기엔 스텁 URL(계약만).
- `stored_files` 추가 시 `V3__document.sql` 없이 부팅하면 `ddl-auto: validate` 실패 → 마이그레이션 먼저.
- RRN 등이 담긴 서류 파일은 접근 로그 + 만료 URL 필수.

## 상태·TODO

- [ ] 전 계층 (RED → GREEN)
- [ ] `V3__document.sql`
- [ ] collab `CaseAccessService`
- [ ] `StubStoragePort` (로컬 인메모리 + `/local-storage/{key}` 컨트롤러) 또는 계약만
