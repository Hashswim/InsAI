package com.insuranceai.form.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.form.application.AiStructurePort.AiStructureResult;
import com.insuranceai.form.domain.FieldType;
import com.insuranceai.form.domain.FormField;
import com.insuranceai.form.domain.FormTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiStructurePortTest {

	private final FormTemplate template = TEMPLATE();

	@Test
	@DisplayName("청구인 입력이 우선이고, LLM이 찾은 값은 0.7로 채우며, 템플릿 밖 키는 버린다")
	void mergesTypedAndInferredValues() {
		ChatModel model = prompt -> new ChatResponse(List.of(new Generation(
				new AssistantMessage("{\"diagnosis\":\"J18.9\",\"visitDate\":\"2026-01-01\",\"injected\":\"x\"}"))));
		var port = new SpringAiStructurePort(ChatClient.create(model), new RuleBasedAiStructurePort(), new ObjectMapper());

		AiStructureResult result = port.structure(Map.of("visitDate", "2026-02-02", "memo", "폐렴 J18.9"), template);

		assertThat(result.fields()).containsExactly(
				Map.entry("diagnosis", "J18.9"), Map.entry("visitDate", "2026-02-02"));
		assertThat(result.confidence()).containsEntry("diagnosis", 0.7).containsEntry("visitDate", 1.0);
		assertThat(result.missingFields()).isEmpty();
	}

	@Test
	@DisplayName("LLM 호출이 실패하면 규칙 기반 결과를 돌려준다")
	void fallsBackWhenModelFails() {
		ChatModel model = prompt -> { throw new IllegalStateException("down"); };
		var port = new SpringAiStructurePort(ChatClient.create(model), new RuleBasedAiStructurePort(), new ObjectMapper());

		AiStructureResult result = port.structure(Map.of(), template);

		assertThat(result.fields()).isEmpty();
		assertThat(result.missingFields()).containsExactly("diagnosis", "visitDate");
	}

	private static FormTemplate TEMPLATE() {
		return FormTemplate.create("DIAGNOSIS", "진단서", List.of(
				new FormField("diagnosis", "질병코드", FieldType.TEXT, true, null, 20, null, null),
				new FormField("visitDate", "진료일", FieldType.DATE, true, null, null, null, null)), 1);
	}
}
