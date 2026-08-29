package com.insuranceai.collab.api;

import com.insuranceai.collab.application.CaseView;
import com.insuranceai.collab.application.GetCaseService;
import com.insuranceai.collab.application.ListCasesService;
import com.insuranceai.common.config.SecurityConfig;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaseQueryController.class)
@Import(SecurityConfig.class)
class CaseQueryControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean ListCasesService listCasesService;
	@MockitoBean GetCaseService getCaseService;

	private static final String USER = "11111111-1111-1111-1111-111111111111";
	private static final String CASE = "22222222-2222-2222-2222-222222222222";

	@Test
	@DisplayName("GET /cases -> 200 목록")
	void listCases() throws Exception {
		given(listCasesService.forUser(any())).willReturn(List.of(new CaseView(
				UUID.fromString(CASE), "자동차 사고", "OPEN",
				UUID.fromString(USER), null, Instant.now())));

		mvc.perform(get("/cases").header("X-User-Id", USER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].title").value("자동차 사고"))
				.andExpect(jsonPath("$.data[0].status").value("OPEN"));
	}

	@Test
	@DisplayName("GET /cases X-User-Id 없으면 -> 400")
	void listCasesMissingHeader() throws Exception {
		mvc.perform(get("/cases"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("GET /cases/{id} -> 200 상세")
	void getCase() throws Exception {
		given(getCaseService.get(any())).willReturn(new CaseView(
				UUID.fromString(CASE), "자동차 사고", "DOCS_REQUESTED",
				UUID.fromString(USER), null, Instant.now()));

		mvc.perform(get("/cases/{id}", CASE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(CASE))
				.andExpect(jsonPath("$.data.status").value("DOCS_REQUESTED"));
	}

	@Test
	@DisplayName("GET /cases/{id} 없음 -> 404")
	void getCaseNotFound() throws Exception {
		given(getCaseService.get(any())).willThrow(new BusinessException(ErrorCode.NOT_FOUND));

		mvc.perform(get("/cases/{id}", CASE))
				.andExpect(status().isNotFound());
	}
}
