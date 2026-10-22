package com.insuranceai.form.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.form.application.AiStructurePort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * insai.ai.enabled=true 일 때만 Spring AI ChatClient로 양식 구조화를 한다.
 * 꺼져 있으면 RuleBasedAiStructurePort가 그대로 쓰인다.
 */
@Configuration
@ConditionalOnProperty(name = "insai.ai.enabled", havingValue = "true")
public class SpringAiConfig {

	@Bean
	public ChatClient chatClient(@Value("${insai.ai.api-key}") String apiKey,
	                             @Value("${insai.ai.model}") String model) {
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
				.openAiApi(OpenAiApi.builder().apiKey(apiKey).build())
				.defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.0).build())
				.build();
		return ChatClient.create(chatModel);
	}

	@Bean
	@Primary
	public AiStructurePort springAiStructurePort(ChatClient chatClient, RuleBasedAiStructurePort fallback,
	                                             ObjectMapper mapper) {
		return new SpringAiStructurePort(chatClient, fallback, mapper);
	}
}
