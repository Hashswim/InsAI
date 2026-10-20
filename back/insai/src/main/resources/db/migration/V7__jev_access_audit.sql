CREATE TABLE jev_access_audits (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    case_id UUID,
    analysis_id UUID,
    action VARCHAR(16) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_jev_access_audits_request_id ON jev_access_audits(request_id);
CREATE INDEX idx_jev_access_audits_actor_id ON jev_access_audits(actor_id);
