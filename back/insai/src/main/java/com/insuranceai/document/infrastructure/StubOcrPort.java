package com.insuranceai.document.infrastructure;

import com.insuranceai.document.application.OcrPort;
import org.springframework.stereotype.Component;

/**
 * OCR 미연동 스텁. 항상 빈 문자열 — 메타데이터만 저장되고 ocrText 는 붙지 않는다.
 * 실제 OCR(Tesseract/클라우드 비전) 붙으면 이 빈을 대체한다.
 */
@Component
public class StubOcrPort implements OcrPort {

	@Override
	public String extractText(String storageKey) {
		return "";
	}
}
