-- 케이스에 청구 보험 분야 추가
ALTER TABLE cases ADD COLUMN claim_type varchar(30);
UPDATE cases SET claim_type = 'OTHER' WHERE claim_type IS NULL;
