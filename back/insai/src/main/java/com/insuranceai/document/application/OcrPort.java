package com.insuranceai.document.application;

/**
 * 파일에서 텍스트를 추출한다. 구현: StubOcrPort(빈 문자열), 이후 Tesseract/클라우드 비전 또는 ai 위임.
 */
public interface OcrPort {

	String extractText(String storageKey);
}
