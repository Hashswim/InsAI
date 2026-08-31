package com.insuranceai.ai.application;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewSummaryServiceTest {

	private final Map<String, Object> data = Map.of("hospital", "서울병원", "visitDate", "2026-01-01");

	@Test
	@DisplayName("LLM JSON 응답을 ReviewSummary 로 파싱한다 (코드펜스/서두 포함해도)")
	void parsesResponse() {
		String raw = """
				```json
				{"summary":"진단서 제출 확인","highlights":["병원: 서울병원"],
				 "issues":["치료비 미기재"],"needsAttention":true}
				```""";
		var service = new ReviewSummaryService(FakeLlmClient.returning(raw));

		ReviewSummary result = service.summarize(data, List.of());

		assertThat(result.summary()).isEqualTo("진단서 제출 확인");
		assertThat(result.highlights()).containsExactly("병원: 서울병원");
		assertThat(result.issues()).containsExactly("치료비 미기재");
		assertThat(result.needsAttention()).isTrue();
	}

	@Test
	@DisplayName("LLM 호출이 실패하면 INTERNAL_ERROR")
	void llmFailure() {
		var service = new ReviewSummaryService(FakeLlmClient.throwing(new RuntimeException("timeout")));

		assertThatThrownBy(() -> service.summarize(data, List.of()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INTERNAL_ERROR);
	}

	@Test
	@DisplayName("응답이 JSON 이 아니면 INTERNAL_ERROR")
	void unparseable() {
		var service = new ReviewSummaryService(FakeLlmClient.returning("죄송합니다, 답변할 수 없습니다."));

		assertThatThrownBy(() -> service.summarize(data, List.of()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INTERNAL_ERROR);
	}
}
