-- 쓰이지 않던 AI 평문 번역·OCR 스텁 컬럼 제거 (LLM 호출은 FastAPI JEV 서비스로 일원화)
ALTER TABLE decisions DROP COLUMN plain_text;
ALTER TABLE stored_files DROP COLUMN ocr_text;
