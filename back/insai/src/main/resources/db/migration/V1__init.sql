-- InsuranceAI 초기 스키마 (auth + collab)
-- Hibernate ddl-auto=create 로 생성한 스키마를 고정한 것. 이후 변경은 V2, V3... 로.

CREATE TABLE accounts (
    id            uuid                     NOT NULL,
    email         varchar(255)             NOT NULL,
    password_hash varchar(255)             NOT NULL,
    name          varchar(255)             NOT NULL,
    role          varchar(20)              NOT NULL,
    status        varchar(20)              NOT NULL,
    created_at    timestamp(6) with time zone,
    updated_at    timestamp(6) with time zone,
    CONSTRAINT accounts_pkey PRIMARY KEY (id),
    CONSTRAINT accounts_email_key UNIQUE (email),
    CONSTRAINT accounts_role_check CHECK (role IN ('CLAIMANT', 'ADJUSTER', 'ADMIN')),
    CONSTRAINT accounts_status_check CHECK (status IN ('ACTIVE', 'PENDING', 'SUSPENDED'))
);

CREATE TABLE cases (
    id          uuid                     NOT NULL,
    claimant_id uuid                     NOT NULL,
    adjuster_id uuid,
    title       varchar(255)             NOT NULL,
    status      varchar(20)              NOT NULL,
    created_at  timestamp(6) with time zone,
    updated_at  timestamp(6) with time zone,
    CONSTRAINT cases_pkey PRIMARY KEY (id),
    CONSTRAINT cases_status_check CHECK (status IN
        ('OPEN', 'DOCS_REQUESTED', 'DOCS_SUBMITTED', 'UNDER_REVIEW', 'DECISION_MADE', 'CLOSED'))
);

CREATE TABLE document_requests (
    id               uuid                     NOT NULL,
    case_id          uuid                     NOT NULL,
    requested_by     uuid                     NOT NULL,
    template_id      uuid                     NOT NULL,
    title            varchar(255)             NOT NULL,
    due_date         date,
    status           varchar(20)              NOT NULL,
    rejection_reason varchar(255),
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    CONSTRAINT document_requests_pkey PRIMARY KEY (id),
    CONSTRAINT document_requests_status_check CHECK (status IN
        ('PENDING', 'SUBMITTED', 'ACCEPTED', 'REJECTED'))
);

CREATE TABLE messages (
    id          uuid                     NOT NULL,
    case_id     uuid                     NOT NULL,
    sender_id   uuid                     NOT NULL,
    sender_role varchar(20)              NOT NULL,
    body        varchar(4000)            NOT NULL,
    ai_mediated boolean                  NOT NULL,
    created_at  timestamp(6) with time zone,
    updated_at  timestamp(6) with time zone,
    CONSTRAINT messages_pkey PRIMARY KEY (id),
    CONSTRAINT messages_sender_role_check CHECK (sender_role IN ('CLAIMANT', 'ADJUSTER', 'ADMIN'))
);

CREATE TABLE decisions (
    id          uuid                     NOT NULL,
    case_id     uuid                     NOT NULL,
    adjuster_id uuid                     NOT NULL,
    raw_text    varchar(8000)            NOT NULL,
    plain_text  varchar(8000),
    created_at  timestamp(6) with time zone,
    updated_at  timestamp(6) with time zone,
    CONSTRAINT decisions_pkey PRIMARY KEY (id)
);

CREATE TABLE timeline_events (
    id         uuid                     NOT NULL,
    case_id    uuid                     NOT NULL,
    type       varchar(30)              NOT NULL,
    actor_id   uuid,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    CONSTRAINT timeline_events_pkey PRIMARY KEY (id),
    CONSTRAINT timeline_events_type_check CHECK (type IN
        ('CASE_OPENED', 'ADJUSTER_ASSIGNED', 'DOCS_REQUESTED', 'DOCS_SUBMITTED',
         'REVIEW_STARTED', 'DECISION_MADE', 'CASE_CLOSED', 'MESSAGE_POSTED'))
);

-- 조회 성능용 인덱스
CREATE INDEX idx_cases_claimant_id ON cases (claimant_id);
CREATE INDEX idx_cases_adjuster_id ON cases (adjuster_id);
CREATE INDEX idx_document_requests_case_id ON document_requests (case_id);
CREATE INDEX idx_messages_case_id ON messages (case_id);
CREATE INDEX idx_decisions_case_id ON decisions (case_id);
CREATE INDEX idx_timeline_events_case_id ON timeline_events (case_id);
