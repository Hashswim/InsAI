package com.insuranceai.form.api;

import com.insuranceai.common.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LifeInsuranceCatalogController.class)
@Import(SecurityConfig.class)
class LifeInsuranceCatalogControllerTest {

	@Autowired
	MockMvc mvc;

	@Test
	@DisplayName("GET /catalog/life/claim-types -> 4개 유형")
	void claimTypes() throws Exception {
		mvc.perform(get("/catalog/life/claim-types"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(4))
				.andExpect(jsonPath("$.data[?(@.code=='DEATH')].label").value("사망보험금"));
	}

	@Test
	@DisplayName("GET /catalog/life/claim-types/DEATH/required-documents -> 공통 + 사망 서류")
	void deathRequiredDocuments() throws Exception {
		mvc.perform(get("/catalog/life/claim-types/{t}/required-documents", "DEATH"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.templateCode=='life.claim-application')]").exists())
				.andExpect(jsonPath("$.data[?(@.templateCode=='life.death-certificate')]").exists())
				.andExpect(jsonPath("$.data[?(@.templateCode=='life.accident-confirmation')].condition")
						.value("재해(외인사)로 사망한 경우"));
	}

	@Test
	@DisplayName("잘못된 청구 유형 -> 400")
	void unknownClaimType() throws Exception {
		mvc.perform(get("/catalog/life/claim-types/{t}/required-documents", "NOPE"))
				.andExpect(status().isBadRequest());
	}
}
