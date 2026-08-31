package com.insuranceai.ai.application;

/**
 * LLM 호출 포트. 구현: StubLlmClient(기본) / AnthropicLlmClient(insai.ai.enabled=true).
 * 실패 시 RuntimeException — 호출 측 서비스가 BusinessException(INTERNAL_ERROR) 로 변환한다.
 */
public interface LlmClient {

	String complete(LlmRequest request);

	/**
	 * @param task 용도 힌트 (스텁이 분기용으로 사용). 예: "review-summary", "structure", "decision-plaintext"
	 */
	record LlmRequest(String task, String systemPrompt, String userPrompt) {
	}
}
