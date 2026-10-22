package com.insuranceai.form.api;

import com.insuranceai.common.config.SecurityConfig;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.form.application.FormSubmissionView;
import com.insuranceai.form.application.GetSubmissionService;
import com.insuranceai.form.application.SaveDraftService;
import com.insuranceai.form.application.SubmitFormService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FormSubmissionController.class)
@Import(SecurityConfig.class)
class FormSubmissionControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean SubmitFormService submitFormService;
	@MockitoBean SaveDraftService saveDraftService;
	@MockitoBean GetSubmissionService getSubmissionService;

	private static final String USER = "11111111-1111-1111-1111-111111111111";
	private static final String ID = "22222222-2222-2222-2222-222222222222";

	private static String submitBody() {
		return "{\"caseId\":\"" + ID + "\",\"templateId\":\"" + ID + "\",\"data\":{\"hospital\":\"서울\"}}";
	}

	@Test
	@DisplayName("POST /submissions -> 201, data.submissionId")
	void submit() throws Exception {
		given(submitFormService.submit(any())).willReturn(UUID.fromString(ID));

		mvc.perform(post("/submissions").header("X-User-Id", USER)
						.contentType(MediaType.APPLICATION_JSON).content(submitBody()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.submissionId").value(ID));
	}

	@Test
	@DisplayName("POST /submissions X-User-Id 없으면 -> 400")
	void submitMissingHeader() throws Exception {
		mvc.perform(post("/submissions")
						.contentType(MediaType.APPLICATION_JSON).content(submitBody()))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /submissions 서비스가 INVALID_INPUT(정의되지 않은 필드) -> 400")
	void submitUnknownField() throws Exception {
		given(submitFormService.submit(any())).willThrow(new BusinessException(ErrorCode.INVALID_INPUT));

		mvc.perform(post("/submissions").header("X-User-Id", USER)
						.contentType(MediaType.APPLICATION_JSON).content(submitBody()))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("PATCH /submissions/{id} 임시저장 -> 200")
	void updateDraft() throws Exception {
		mvc.perform(patch("/submissions/{id}", ID)
						.contentType(MediaType.APPLICATION_JSON).content("{\"data\":{\"hospital\":\"서울\"}}"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("GET /submissions/{id} -> 200, missingFields 노출")
	void getSubmission() throws Exception {
		given(getSubmissionService.get(any())).willReturn(new FormSubmissionView(
				UUID.fromString(ID), null, UUID.fromString(ID), UUID.fromString(ID),
				"SUBMITTED", 1, false, Map.of("hospital", "서울"), null,
				List.of("visitDate"), List.of()));

		mvc.perform(get("/submissions/{id}", ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SUBMITTED"))
				.andExpect(jsonPath("$.data.missingFields[0]").value("visitDate"));
	}

	@Test
	@DisplayName("GET /submissions/{id} 없음 -> 404")
	void getSubmissionNotFound() throws Exception {
		given(getSubmissionService.get(any())).willThrow(new BusinessException(ErrorCode.NOT_FOUND));

		mvc.perform(get("/submissions/{id}", ID)).andExpect(status().isNotFound());
	}
}
