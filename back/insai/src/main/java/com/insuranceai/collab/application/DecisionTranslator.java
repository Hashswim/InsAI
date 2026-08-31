package com.insuranceai.collab.application;

/**
 * 사정 결과 원문 → 청구인이 이해할 평문. 구현: NoopDecisionTranslator(기본) / ai 의 LLM 구현.
 */
public interface DecisionTranslator {

	/** 평문 번역 결과. 불가/비활성이면 null */
	String toPlainText(String rawText);
}
