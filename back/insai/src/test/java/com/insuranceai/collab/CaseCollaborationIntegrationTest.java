package com.insuranceai.collab;

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

class CaseCollaborationIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper om;

	private final UUID claimant = UUID.randomUUID();
	private final UUID adjuster = UUID.randomUUID();
	private final UUID templateId = UUID.randomUUID();

	@Test
	@DisplayName("케이스 개설 → 사정사 배정 → 서류 요청 → 청구인 조회 → 메시지 → 타임라인에 4개 이벤트 순서대로")
	void collaborationFlow() throws Exception {
		String caseId = openCase();

		mvc.perform(patch("/cases/{id}/assign", caseId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
						{"adjusterId":"%s"}""".formatted(adjuster)))
				.andExpect(status().isOk());

		mvc.perform(post("/cases/{id}/document-requests", caseId)
						.header("X-User-Id", adjuster.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
						{"templateId":"%s","title":"진단서"}""".formatted(templateId)))
				.andExpect(status().isCreated());

		mvc.perform(get("/cases/{id}/document-requests", caseId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].status").value("PENDING"));

		mvc.perform(post("/cases/{id}/messages", caseId)
						.header("X-User-Id", claimant.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
						{"senderRole":"CLAIMANT","body":"서류 확인했습니다"}"""))
				.andExpect(status().isCreated());

		mvc.perform(get("/cases/{id}/timeline", caseId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(4))
				.andExpect(jsonPath("$.data[0].type").value("CASE_OPENED"))
				.andExpect(jsonPath("$.data[1].type").value("ADJUSTER_ASSIGNED"))
				.andExpect(jsonPath("$.data[2].type").value("DOCS_REQUESTED"))
				.andExpect(jsonPath("$.data[3].type").value("MESSAGE_POSTED"));
	}

	@Test
	@DisplayName("청구인이 서류 요청을 생성하려 하면 403")
	void claimantCannotCreateDocumentRequest() throws Exception {
		String caseId = openCaseAndAssign();

		mvc.perform(post("/cases/{id}/document-requests", caseId)
						.header("X-User-Id", claimant.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
						{"templateId":"%s","title":"진단서"}""".formatted(templateId)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("비참여자가 메시지를 작성하려 하면 403")
	void outsiderCannotPostMessage() throws Exception {
		String caseId = openCaseAndAssign();

		mvc.perform(post("/cases/{id}/messages", caseId)
						.header("X-User-Id", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
						{"senderRole":"CLAIMANT","body":"안녕하세요"}"""))
				.andExpect(status().isForbidden());
	}

	private String openCase() throws Exception {
		String body = mvc.perform(post("/cases")
						.header("X-User-Id", claimant.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
						{"title":"자동차 사고 청구"}"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return om.readTree(body).path("data").path("caseId").asText();
	}

	private String openCaseAndAssign() throws Exception {
		String caseId = openCase();
		mvc.perform(patch("/cases/{id}/assign", caseId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
						{"adjusterId":"%s"}""".formatted(adjuster)))
				.andExpect(status().isOk());
		return caseId;
	}
}
