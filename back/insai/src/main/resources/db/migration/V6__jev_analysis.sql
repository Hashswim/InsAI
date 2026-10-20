CREATE TABLE jev_analyses (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES cases(id),
    requested_by UUID NOT NULL REFERENCES accounts(id),
    result_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_jev_analyses_case_id ON jev_analyses(case_id);
