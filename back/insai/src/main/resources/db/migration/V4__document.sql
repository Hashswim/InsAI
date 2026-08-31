-- document 도메인: 업로드된 파일 메타데이터
-- 실제 바이트는 storage_key 로 오브젝트 스토리지(로컬 디스크)에, OCR 텍스트는 추출 후 부착

CREATE TABLE stored_files (
    id            uuid                     NOT NULL,
    case_id       uuid                     NOT NULL,
    submission_id uuid,
    uploader_id   uuid                     NOT NULL,
    filename      varchar(255)             NOT NULL,
    content_type  varchar(120)             NOT NULL,
    size_bytes    bigint                   NOT NULL,
    storage_key   varchar(255)             NOT NULL,
    ocr_text      varchar(20000),
    created_at    timestamp(6) with time zone,
    updated_at    timestamp(6) with time zone,
    CONSTRAINT stored_files_pkey PRIMARY KEY (id),
    CONSTRAINT uk_stored_files_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_stored_files_case_id ON stored_files (case_id);
CREATE INDEX idx_stored_files_submission_id ON stored_files (submission_id);
