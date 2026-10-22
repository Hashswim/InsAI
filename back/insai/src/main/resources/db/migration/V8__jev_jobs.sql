CREATE TABLE jev_jobs (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES cases(id),
    requested_by UUID NOT NULL REFERENCES accounts(id),
    idempotency_key UUID NOT NULL,
    request_id UUID NOT NULL,
    command_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','UNKNOWN')),
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    analysis_id UUID REFERENCES jev_analyses(id),
    error_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_jev_jobs_actor_key UNIQUE (requested_by, idempotency_key)
);
CREATE INDEX idx_jev_jobs_queue ON jev_jobs(status, created_at);
