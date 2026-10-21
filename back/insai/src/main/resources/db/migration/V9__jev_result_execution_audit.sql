-- Nullable for historical rows: do not fabricate release identities or historical events.
ALTER TABLE jev_analyses ADD COLUMN request_id UUID;
ALTER TABLE jev_analyses ADD COLUMN job_id UUID REFERENCES jev_jobs(id);
ALTER TABLE jev_analyses ADD CONSTRAINT uq_jev_analysis_job UNIQUE(job_id);
ALTER TABLE jev_analyses ADD COLUMN provider VARCHAR(20);
ALTER TABLE jev_analyses ADD COLUMN model VARCHAR(200);
ALTER TABLE jev_analyses ADD COLUMN prompt_version VARCHAR(100);
ALTER TABLE jev_analyses ADD COLUMN prompt_sha256 VARCHAR(64);
ALTER TABLE jev_analyses ADD COLUMN chain_version VARCHAR(100);
ALTER TABLE jev_analyses ADD COLUMN trace_id UUID;
ALTER TABLE jev_analyses ADD COLUMN trace_status VARCHAR(16);
CREATE INDEX idx_jev_analyses_request ON jev_analyses(request_id);
ALTER TABLE jev_access_audits ADD COLUMN job_id UUID;
CREATE INDEX idx_jev_access_audits_job ON jev_access_audits(job_id);
CREATE TABLE jev_execution_events (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    job_id UUID,
    analysis_id UUID,
    case_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    type VARCHAR(32) NOT NULL CHECK(type IN ('ENQUEUED','STARTED','RESULT_STORED','SUCCEEDED','FAILED','UNKNOWN')),
    error_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_jev_events_job ON jev_execution_events(job_id,created_at);
CREATE INDEX idx_jev_events_analysis ON jev_execution_events(analysis_id,created_at);
CREATE INDEX idx_jev_events_request ON jev_execution_events(request_id,created_at);
