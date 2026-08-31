package com.insuranceai.ai.infrastructure;

import com.insuranceai.ai.application.LlmClient;
import com.insuranceai.ai.application.LlmClient.LlmRequest;
import com.insuranceai.collab.application.DecisionTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * insai.ai.enabled=true 일 때 사정 결과 원문을 청구인용 평문으로 옮긴다.
 * 실패하면 null (호출 측이 plainText 를 비워둔다).
 */
@Component
@ConditionalOnProperty(prefix = "insai.ai", name = "enabled", havingValue = "true")
public class LlmDecisionTranslator implements DecisionTranslator {

	private static final Logger log = LoggerFactory.getLogger(LlmDecisionTranslator.class);

	private static final String SYSTEM = """
			너는 손해사정사의 사정 결과를 보험 청구인이 이해하기 쉬운 평문으로 옮기는 보조자다.
			원문에 있는 사실만 쓰고, 새 결론이나 금액을 만들지 마라.
			가능하면 근거가 된 약관 조항을 함께 언급하되, 원문에 없으면 생략하라.
			3~5문장의 존댓말 한국어로만 답하라.
			""";

	private final LlmClient llm;

	public LlmDecisionTranslator(LlmClient llm) {
		this.llm = llm;
	}

	@Override
	public String toPlainText(String rawText) {
		if (rawText == null || rawText.isBlank()) {
			return null;
		}
		try {
			String out = llm.complete(new LlmRequest("decision-plaintext", SYSTEM, rawText));
			return (out == null || out.isBlank()) ? null : out.trim();
		} catch (RuntimeException e) {
			log.warn("사정 결과 평문 번역 실패", e);
			return null;
		}
	}
}
