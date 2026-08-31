package com.insuranceai.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI(LLM) 설정. 기본 비활성 — enabled=true 이고 apiKey 가 있어야 실제 Claude 호출.
 */
@ConfigurationProperties("insai.ai")
public record AiProperties(boolean enabled, String apiKey, String model) {

	public AiProperties {
		if (model == null || model.isBlank()) {
			model = "claude-opus-5";
		}
	}

	public boolean usable() {
		return enabled && apiKey != null && !apiKey.isBlank();
	}
}
