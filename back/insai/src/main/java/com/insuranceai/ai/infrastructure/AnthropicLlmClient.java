package com.insuranceai.ai.infrastructure;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.insuranceai.ai.AiProperties;
import com.insuranceai.ai.application.LlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 실제 Claude(Anthropic) 호출. insai.ai.enabled=true 일 때만 빈으로 등록된다.
 * 키·모델은 AiProperties (INSAI_AI_API_KEY / INSAI_AI_MODEL).
 *
 * TODO: 키 수령 후 실호출 검증 (모델 ID·응답 형태). 스킬 claude-api 참고.
 */
@Component
@ConditionalOnProperty(prefix = "insai.ai", name = "enabled", havingValue = "true")
public class AnthropicLlmClient implements LlmClient {

	private final AnthropicClient client;
	private final String model;

	public AnthropicLlmClient(AiProperties properties) {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			throw new IllegalStateException("insai.ai.enabled=true 인데 insai.ai.api-key 가 없습니다.");
		}
		this.client = AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).build();
		this.model = properties.model();
	}

	@Override
	public String complete(LlmRequest request) {
		MessageCreateParams params = MessageCreateParams.builder()
				.model(model)
				.maxTokens(16000L)
				.system(request.systemPrompt() == null ? "" : request.systemPrompt())
				.addUserMessage(request.userPrompt() == null ? "" : request.userPrompt())
				.build();

		Message response = client.messages().create(params);

		StringBuilder out = new StringBuilder();
		for (ContentBlock block : response.content()) {
			block.text().ifPresent(t -> out.append(t.text()));
		}
		return out.toString();
	}
}
