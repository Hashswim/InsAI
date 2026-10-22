-- form 도메인: 양식 템플릿 + 제출물
-- JSON 은 당분간 varchar 에 문자열로 저장 (jsonb 이관은 추후)

CREATE TABLE form_templates (
    id         uuid                     NOT NULL,
    code       varchar(100)             NOT NULL,
    name       varchar(255)             NOT NULL,
    version    integer                  NOT NULL,
    fields     varchar(8000)            NOT NULL,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    CONSTRAINT form_templates_pkey PRIMARY KEY (id)
);

CREATE TABLE form_submissions (
    id                  uuid                     NOT NULL,
    document_request_id uuid,
    case_id             uuid                     NOT NULL,
    template_id         uuid                     NOT NULL,
    submitted_by        uuid                     NOT NULL,
    data                varchar(8000),
    ai_structured       varchar(8000),
    missing_fields      varchar(2000),
    validation_errors   varchar(2000),
    status              varchar(20)              NOT NULL,
    version             integer                  NOT NULL,
    complete            boolean                  NOT NULL,
    created_at          timestamp(6) with time zone,
    updated_at          timestamp(6) with time zone,
    CONSTRAINT form_submissions_pkey PRIMARY KEY (id),
    CONSTRAINT form_submissions_status_check CHECK (status IN ('DRAFT', 'SUBMITTED'))
);

CREATE INDEX idx_form_templates_code ON form_templates (code);
CREATE INDEX idx_form_submissions_document_request_id ON form_submissions (document_request_id);
CREATE INDEX idx_form_submissions_case_id ON form_submissions (case_id);
