# 모듈: back/insai · com.insuranceai.document  (구현 완료 — GREEN)

## 목적

첨부 파일 저장. Presigned URL 발급, 업로드 확인, 다운로드(케이스 참여자만), OCR 텍스트 부착.
**책임 밖**: 파일 내용 해석/요약(ai), 양식 값(form).

## 경로

- `back/insai/src/main/java/com/insuranceai/document/`
- 스키마: `V4__document.sql` — `stored_files`

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
| `IssueDownloadUrlService` | 파일 NOT_FOUND → `CaseParticipantGuard.requireParticipant(caseId, userId)` (collab) → `StoragePort.downloadUrl` |
| `ConfirmUploadService` | 참여자 확인 + `StoredFile.register` + (best-effort) `OcrPort.extractText` → `attachOcrText` |
| `ListCaseFilesService` | `byCase` / `bySubmission` → `List<StoredFileView>` |
| `StoragePort` (포트) | `uploadUrl(key)` / `downloadUrl(key)` — 구현: `LocalDiskStorage` (`insai.storage.local-dir`, URL `/local-storage/{key}`). 이후 MinIO/S3 presigner |
| `OcrPort` (포트) | `extractText(storageKey)` — 구현: `StubOcrPort`(빈 문자열). 이후 Tesseract/클라우드 비전 또는 ai 위임 |
| `CaseParticipantGuard` (포트) | 구현 `CollabCaseParticipantGuard` → `collab.application.CaseAccessService` |

| API | |
|---|---|
| `POST /files/upload-url` | X-User-Id + `{caseId, submissionId?, filename, contentType, sizeBytes}` → `{url, storageKey}` |
| `POST /files` | X-User-Id + `{…, storageKey}` → 201 `{fileId}` |
| `GET /files/{id}/download-url` | X-User-Id, 참여자 → `{url}`, 비참여자 → 403 |
| `GET /files?caseId=` \| `?submissionId=` | `List<StoredFileView>`, 둘 다 없으면 400 |
| `PUT` / `GET /local-storage/**` | `LocalStorageController` — 로컬 디스크 바이트 저장/조회 |

## 의존 관계 (계획)

- 나감: `common`, `collab.application` (참여자 확인 포트). `form` 은 submissionId 로 느슨히 연결.
- 필요: collab 에 `CaseAccessService.requireParticipant(caseId, userId)` 추가.

## 주의 (gotchas)

- Presigned URL 실제 발급은 MinIO/S3 자격증명 필요 → 현재는 `LocalDiskStorage` + `/local-storage/**` 컨트롤러.
- `LocalDiskStorage` 는 `..` 경로 탈출을 루트 대비 `startsWith` 로 차단(INVALID_INPUT).
- `stored_files` 는 `V4__document.sql` — 없이 부팅하면 `ddl-auto: validate` 실패.
- 프론트: FormField `FILE` 값에는 fileId(UUID)를 저장. 사정사 화면은 UUID 패턴이면 “첨부 파일 열기” 버튼.
- RRN 등이 담긴 서류 파일은 접근 로그 + 만료 URL 필수(추후).

## 상태·TODO

- [x] 전 계층 (GREEN) — `+28` tests (총 217/217)
- [x] `V4__document.sql`
- [x] collab `CaseAccessService`
- [x] `LocalDiskStorage` + `LocalStorageController` + `StubOcrPort`
- [x] 프론트 `api/files.js` + `FormFieldInput` FILE 업로드 + 사정사 다운로드
- [ ] 실제 OCR / 오브젝트 스토리지 presigner
- [ ] 첨부 파일 접근 로그 + 만료 URL
