package com.insuranceai.ai.api;

import com.insuranceai.ai.application.ReviewSummary;
import com.insuranceai.ai.application.ReviewSummaryService;
import com.insuranceai.common.config.SecurityConfig;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.form.application.FormSubmissionView;
import com.insuranceai.form.application.GetSubmissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiController.class)
@Import(SecurityConfig.class)
class AiControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean ReviewSummaryService reviewSummaryService;
	@MockitoBean GetSubmissionService getSubmissionService;

	private static final String ID = "22222222-2222-2222-2222-222222222222";

	@Test
	@DisplayName("POST /ai/review-summary -> 200, 요약 반환")
	void reviewSummary() throws Exception {
		given(getSubmissionService.get(any())).willReturn(new FormSubmissionView(
				UUID.fromString(ID), null, UUID.fromString(ID), UUID.fromString(ID),
				"SUBMITTED", 1, false, Map.of("hospital", "서울"), null, List.of(), List.of()));
		given(reviewSummaryService.summarize(any(), any())).willReturn(
				new ReviewSummary("요약", List.of("핵심"), List.of("누락"), true));

		mvc.perform(post("/ai/review-summary").contentType(MediaType.APPLICATION_JSON)
						.content("{\"submissionId\":\"" + ID + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.summary").value("요약"))
				.andExpect(jsonPath("$.data.needsAttention").value(true));
	}

	@Test
	@DisplayName("submissionId 없으면 -> 400")
	void missingSubmissionId() throws Exception {
		mvc.perform(post("/ai/review-summary").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("없는 제출물 -> 404")
	void submissionNotFound() throws Exception {
		given(getSubmissionService.get(any())).willThrow(new BusinessException(ErrorCode.NOT_FOUND));

		mvc.perform(post("/ai/review-summary").contentType(MediaType.APPLICATION_JSON)
						.content("{\"submissionId\":\"" + ID + "\"}"))
				.andExpect(status().isNotFound());
	}
}
