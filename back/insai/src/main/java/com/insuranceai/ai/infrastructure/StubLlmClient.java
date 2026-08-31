package com.insuranceai.ai.infrastructure;

import com.insuranceai.ai.application.LlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 기본 활성. 실제 LLM 없이 계약(응답 형태)만 만족시킨다.
 * insai.ai.enabled=true 이면 AnthropicLlmClient 로 대체된다.
 */
@Component
@ConditionalOnProperty(prefix = "insai.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class StubLlmClient implements LlmClient {

	@Override
	public String complete(LlmRequest request) {
		return switch (request.task() == null ? "" : request.task()) {
			case "review-summary" -> """
					{"summary":"제출 서류 요약(스텁). AI 미연동 상태입니다.",
					 "highlights":[],"issues":["AI 검토 미연동 — 사정사 직접 확인 필요"],"needsAttention":true}""";
			case "decision-plaintext" -> "";
			default -> "{}";
		};
	}
}
