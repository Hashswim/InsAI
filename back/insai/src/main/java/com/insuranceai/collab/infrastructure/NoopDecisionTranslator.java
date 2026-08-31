package com.insuranceai.collab.infrastructure;

import com.insuranceai.collab.application.DecisionTranslator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI 비활성 기본값 — 평문 번역 없음(plainText 는 null 로 남는다).
 */
@Component
@ConditionalOnProperty(prefix = "insai.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopDecisionTranslator implements DecisionTranslator {

	@Override
	public String toPlainText(String rawText) {
		return null;
	}
}
