package com.insuranceai.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FormSubmissionIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper om;

	private final UUID claimant = UUID.randomUUID();
	private final UUID adjuster = UUID.randomUUID();

	@Test
	@DisplayName("템플릿 등록 → 케이스/서류요청 → 청구인 제출 → 케이스 DOCS_SUBMITTED, 누락필드 1개, AI 구조화 저장")
	void submitFlow() throws Exception {
		String templateId = json(mvc.perform(post("/templates")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
						{"code":"medical-cert","name":"진단서","fields":[
						 {"key":"hospital","label":"병원명","type":"TEXT","required":true},
						 {"key":"visitDate","label":"내원일","type":"DATE","required":true}]}"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString())
				.path("data").path("templateId").asText();

		String caseId = json(mvc.perform(post("/cases").header("X-User-Id", claimant.toString())
						.contentType(MediaType.APPLICATION_JSON).content("""
						{"title":"자동차 사고 청구"}"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString())
				.path("data").path("caseId").asText();

		mvc.perform(patch("/cases/{id}/assign", caseId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
						{"adjusterId":"%s"}""".formatted(adjuster)))
				.andExpect(status().isOk());

		String docReqId = json(mvc.perform(post("/cases/{id}/document-requests", caseId)
						.header("X-User-Id", adjuster.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
						{"templateId":"%s","title":"진단서 제출"}""".formatted(templateId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString())
				.path("data").path("documentRequestId").asText();

		// 청구인이 hospital 만 채워 제출 (visitDate 누락)
		String submissionId = json(mvc.perform(post("/submissions")
						.header("X-User-Id", claimant.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
						{"documentRequestId":"%s","caseId":"%s","templateId":"%s","data":{"hospital":"서울병원"}}"""
						.formatted(docReqId, caseId, templateId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString())
				.path("data").path("submissionId").asText();

		// 사정사 검토 화면
		mvc.perform(get("/submissions/{id}", submissionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SUBMITTED"))
				.andExpect(jsonPath("$.data.complete").value(false))
				.andExpect(jsonPath("$.data.data.hospital").value("서울병원"))
				.andExpect(jsonPath("$.data.missingFields.length()").value(1))
				.andExpect(jsonPath("$.data.missingFields[0]").value("visitDate"))
				.andExpect(jsonPath("$.data.aiStructured.hospital").value("서울병원"));

		// 제출로 서류요청/케이스 상태가 전이됨
		mvc.perform(get("/cases/{id}/document-requests", caseId))
				.andExpect(jsonPath("$.data[0].status").value("SUBMITTED"));

		mvc.perform(get("/cases/{id}/timeline", caseId))
				.andExpect(jsonPath("$.data.length()").value(4))
				.andExpect(jsonPath("$.data[3].type").value("DOCS_SUBMITTED"));
	}

	private JsonNode json(String body) throws Exception {
		return om.readTree(body);
	}
}
