package com.insuranceai.document.api;

import com.insuranceai.common.config.SecurityConfig;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.document.application.ConfirmUploadService;
import com.insuranceai.document.application.IssueDownloadUrlService;
import com.insuranceai.document.application.IssueUploadUrlService;
import com.insuranceai.document.application.ListCaseFilesService;
import com.insuranceai.document.application.StoredFileView;
import com.insuranceai.document.application.UploadTarget;
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
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@Import(SecurityConfig.class)
class FileControllerTest {

	@Autowired MockMvc mvc;

	@MockitoBean IssueUploadUrlService issueUploadUrlService;
	@MockitoBean ConfirmUploadService confirmUploadService;
	@MockitoBean IssueDownloadUrlService issueDownloadUrlService;
	@MockitoBean ListCaseFilesService listCaseFilesService;

	private static final String USER = "aaaa1111-1111-1111-1111-111111111111";
	private static final String CASE = "6b65e463-5907-425e-aed1-1cc6249ae2ba";

	@Test
	@DisplayName("POST /files/upload-url -> 200, url + storageKey")
	void issueUploadUrl() throws Exception {
		given(issueUploadUrlService.issue(any()))
				.willReturn(new UploadTarget("/local-storage/k.pdf", "k.pdf"));

		mvc.perform(post("/files/upload-url").header("X-User-Id", USER)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"caseId\":\"" + CASE + "\",\"filename\":\"a.pdf\","
								+ "\"contentType\":\"application/pdf\",\"sizeBytes\":10}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.storageKey").value("k.pdf"));
	}

	@Test
	@DisplayName("POST /files/upload-url: X-User-Id 없으면 400")
	void missingHeader() throws Exception {
		mvc.perform(post("/files/upload-url").contentType(MediaType.APPLICATION_JSON)
						.content("{\"caseId\":\"" + CASE + "\",\"filename\":\"a.pdf\","
								+ "\"contentType\":\"application/pdf\",\"sizeBytes\":10}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /files/upload-url: filename 누락 -> 400")
	void missingFilename() throws Exception {
		mvc.perform(post("/files/upload-url").header("X-User-Id", USER)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"caseId\":\"" + CASE + "\",\"contentType\":\"application/pdf\",\"sizeBytes\":10}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /files -> 201, fileId")
	void confirmUpload() throws Exception {
		UUID fileId = UUID.randomUUID();
		given(confirmUploadService.confirm(any())).willReturn(fileId);

		mvc.perform(post("/files").header("X-User-Id", USER)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"caseId\":\"" + CASE + "\",\"filename\":\"a.pdf\","
								+ "\"contentType\":\"application/pdf\",\"sizeBytes\":10,\"storageKey\":\"k.pdf\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.fileId").value(fileId.toString()));
	}

	@Test
	@DisplayName("GET /files/{id}/download-url -> 200; 비참여자 -> 403")
	void downloadUrl() throws Exception {
		UUID fileId = UUID.randomUUID();
		given(issueDownloadUrlService.issue(any(), any())).willReturn("/local-storage/k.pdf");

		mvc.perform(get("/files/" + fileId + "/download-url").header("X-User-Id", USER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.url").value("/local-storage/k.pdf"));

		given(issueDownloadUrlService.issue(any(), any()))
				.willThrow(new BusinessException(ErrorCode.FORBIDDEN));
		mvc.perform(get("/files/" + fileId + "/download-url").header("X-User-Id", USER))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("GET /files?caseId= -> 200 리스트")
	void listByCase() throws Exception {
		given(listCaseFilesService.byCase(any())).willReturn(List.of(new StoredFileView(
				UUID.randomUUID(), UUID.fromString(CASE), null, "a.pdf", "application/pdf", 10, false, Instant.now())));

		mvc.perform(get("/files").param("caseId", CASE).header("X-User-Id", USER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].filename").value("a.pdf"));
	}

	@Test
	@DisplayName("GET /files: caseId/submissionId 둘 다 없으면 400")
	void listWithoutParams() throws Exception {
		mvc.perform(get("/files").header("X-User-Id", USER))
				.andExpect(status().isBadRequest());
	}
}
