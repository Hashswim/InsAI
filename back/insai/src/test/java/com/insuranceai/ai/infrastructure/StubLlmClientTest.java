package com.insuranceai.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.ai.application.LlmClient.LlmRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubLlmClientTest {

	private final StubLlmClient stub = new StubLlmClient();
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	@DisplayName("review-summary task 는 파싱 가능한 계약 JSON 을 돌려준다")
	void reviewSummaryContract() throws Exception {
		String raw = stub.complete(new LlmRequest("review-summary", "sys", "user"));
		JsonNode json = mapper.readTree(raw);

		assertThat(json.has("summary")).isTrue();
		assertThat(json.get("highlights").isArray()).isTrue();
		assertThat(json.get("issues").isArray()).isTrue();
		assertThat(json.get("needsAttention").asBoolean()).isTrue();
	}

	@Test
	@DisplayName("알 수 없는 task 는 빈 JSON")
	void unknownTask() {
		assertThat(stub.complete(new LlmRequest("nope", "s", "u"))).isEqualTo("{}");
	}
}
