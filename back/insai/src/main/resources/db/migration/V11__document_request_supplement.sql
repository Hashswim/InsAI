ALTER TABLE document_requests DROP CONSTRAINT document_requests_status_check;
ALTER TABLE document_requests ADD CONSTRAINT document_requests_status_check CHECK (status IN
    ('PENDING', 'SUBMITTED', 'ACCEPTED', 'REJECTED', 'SUPPLEMENT_REQUESTED'));
