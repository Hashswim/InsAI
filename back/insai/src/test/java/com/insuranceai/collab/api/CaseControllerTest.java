package com.insuranceai.collab.api;

import com.insuranceai.collab.application.AssignAdjusterService;
import com.insuranceai.collab.application.CloseCaseService;
import com.insuranceai.collab.application.CreateDocumentRequestService;
import com.insuranceai.collab.application.DocumentRequestAction;
import com.insuranceai.collab.application.DocumentRequestView;
import com.insuranceai.collab.application.GetCaseTimelineService;
import com.insuranceai.collab.application.ListCaseDocumentRequestsService;
import com.insuranceai.collab.application.OpenCaseService;
import com.insuranceai.collab.application.PostMessageService;
import com.insuranceai.collab.application.RegisterDecisionService;
import com.insuranceai.collab.application.StartReviewService;
import com.insuranceai.collab.application.TimelineEntry;
import com.insuranceai.collab.application.UpdateDocumentRequestStatusService;
import com.insuranceai.collab.domain.TimelineEventType;
import com.insuranceai.common.config.SecurityConfig;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaseController.class)
@Import(SecurityConfig.class)
class CaseControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean OpenCaseService openCaseService;
	@MockitoBean AssignAdjusterService assignAdjusterService;
	@MockitoBean CreateDocumentRequestService createDocumentRequestService;
	@MockitoBean ListCaseDocumentRequestsService listCaseDocumentRequestsService;
	@MockitoBean UpdateDocumentRequestStatusService updateDocumentRequestStatusService;
	@MockitoBean PostMessageService postMessageService;
	@MockitoBean RegisterDecisionService registerDecisionService;
	@MockitoBean StartReviewService startReviewService;
	@MockitoBean CloseCaseService closeCaseService;
	@MockitoBean GetCaseTimelineService getCaseTimelineService;

	private static final String USER = "11111111-1111-1111-1111-111111111111";
	private static final String CASE = "22222222-2222-2222-2222-222222222222";

	@Test
	@DisplayName("POST /cases -> 201, data.caseId")
	void openCase() throws Exception {
		given(openCaseService.open(any())).willReturn(UUID.fromString(CASE));

		mvc.perform(post("/cases").header("X-User-Id", USER)
						.contentType(MediaType.APPLICATION_JSON).content("""
						{"title":"자동차 사고 청구"}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.caseId").value(CASE));
	}

	@Test
	@DisplayName("POST /cases 제목 공백 -> 400")
	void openCaseBlankTitle() throws Exception {
		mvc.perform(post("/cases").header("X-User-Id", USER)
						.contentType(MediaType.APPLICATION_JSON).content("""
						{"title":"   "}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /cases X-User-Id 헤더 없으면 -> 400")
	void openCaseMissingUserHeader() throws Exception {
		mvc.perform(post("/cases")
						.contentType(MediaType.APPLICATION_JSON).content("""
						{"title":"자동차 사고 청구"}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("PATCH /cases/{id}/assign -> 200")
	void assignAdjuster() throws Exception {
		mvc.perform(patch("/cases/{id}/assign", CASE)
						.contentType(MediaType.APPLICATION_JSON).content("""
						{"adjusterId":"%s"}""".formatted(USER)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("PATCH /cases/{id}/assign adjusterId 누락 -> 400")
	void assignAdjusterMissingId() throws Exception {
		mvc.perform(patch("/cases/{id}/assign", CASE)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /cases/{id}/document-requests -> 201, data.documentRequestId")
	void createDocumentRequest() throws Exception {
		given(createDocumentRequestService.create(any())).willReturn(UUID.fromString(USER));

		mvc.perform(post("/cases/{id}/document-requests", CASE).header("X-User-Id", USER)
						.contentType(MediaType.APPLICATION_JSON).content("""
						{"templateId":"%s","title":"진단서"}""".formatted(CASE)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.documentRequestId").value(USER));
	}

	@Test
	@DisplayName("POST /cases/{id}/document-requests 서비스가 FORBIDDEN -> 403")
	void createDocumentRequestForbidden() throws Exception {
		given(createDocumentRequestService.create(any()))
				.willThrow(new BusinessException(ErrorCode.FORBIDDEN));

		mvc.perform(post("/cases/{id}/document-requests", CASE).header("X-User-Id", USER)
						.contentType(MediaType.APPLICATION_JSON).content("""
						{"templateId":"%s","title":"진단서"}""".formatted(CASE)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("GET /cases/{id}/document-requests -> 200 목록")
	void listDocumentRequests() throws Exception {
		given(listCaseDocumentRequestsService.list(any())).willReturn(List.of(
				new DocumentRequestView(UUID.fromString(USER), UUID.fromString(CASE), UUID.fromString(CASE),
						"진단서", "PENDING", null, null, java.time.Instant.now())));

		mvc.perform(get("/cases/{id}/document-requests", CASE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].title").value("진단서"))
				.andExpect(jsonPath("$.data[0].status").value("PENDING"));
	}

	@Test
	@DisplayName("PATCH /document-requests/{id} ACCEPT -> 200")
	void acceptDocumentRequest() throws Exception {
		mvc.perform(patch("/document-requests/{id}", USER)
						.contentType(MediaType.APPLICATION_JSON).content("""
						{"action":"ACCEPT"}"""))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("PATCH /document-requests/{id} REJECT 사유 없으면 서비스가 INVALID_INPUT -> 400")
	void rejectWithoutReason() throws Exception {
		willThrow(new BusinessException(ErrorCode.INVALID_INPUT))
				.given(updateDocumentRequestStatusService)
				.updateStatus(any(), eq(DocumentRequestAction.REJECT), any());

		mvc.perform(patch("/document-requests/{id}", USER)
						.contentType(MediaType.APPLICATION_JSON).content("""
						{"action":"REJECT"}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /cases/{id}/messages -> 201, data.messageId")
	void postMessage() throws Exception {
		given(postMessageService.post(any())).willReturn(UUID.fromString(USER));

		mvc.perform(post("/cases/{id}/messages", CASE).header("X-User-Id", USER)
						.contentType(MediaType.APPLICATION_JSON).content("""
						{"senderRole":"CLAIMANT","body":"서류 올렸습니다"}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.messageId").value(USER));
	}

	@Test
	@DisplayName("POST /cases/{id}/messages 본문 공백 -> 400")
	void postMessageBlankBody() throws Exception {
		mvc.perform(post("/cases/{id}/messages", CASE).header("X-User-Id", USER)
						.contentType(MediaType.APPLICATION_JSON).content("""
						{"senderRole":"CLAIMANT","body":"   "}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /cases/{id}/review -> 200")
	void startReview() throws Exception {
		mvc.perform(post("/cases/{id}/review", CASE).header("X-User-Id", USER))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("POST /cases/{id}/close -> 200")
	void closeCase() throws Exception {
		mvc.perform(post("/cases/{id}/close", CASE).header("X-User-Id", USER))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("POST /cases/{id}/decisions -> 201, data.decisionId")
	void registerDecision() throws Exception {
		given(registerDecisionService.register(any())).willReturn(UUID.fromString(USER));

		mvc.perform(post("/cases/{id}/decisions", CASE).header("X-User-Id", USER)
						.contentType(MediaType.APPLICATION_JSON).content("""
						{"rawText":"지급 승인. 총 320만원."}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.decisionId").value(USER));
	}

	@Test
	@DisplayName("GET /cases/{id}/timeline -> 200 시간순")
	void timeline() throws Exception {
		given(getCaseTimelineService.timeline(any())).willReturn(List.of(
				new TimelineEntry(TimelineEventType.CASE_OPENED, UUID.fromString(USER), Instant.now())));

		mvc.perform(get("/cases/{id}/timeline", CASE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].type").value("CASE_OPENED"));
	}
}
