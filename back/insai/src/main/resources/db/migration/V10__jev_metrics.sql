ALTER TABLE jev_analyses ADD COLUMN input_tokens INTEGER CHECK(input_tokens >= 0);
ALTER TABLE jev_analyses ADD COLUMN output_tokens INTEGER CHECK(output_tokens >= 0);
CREATE INDEX idx_jev_analyses_case_created ON jev_analyses(case_id,created_at);
CREATE INDEX idx_jev_jobs_case_created ON jev_jobs(case_id,created_at);
