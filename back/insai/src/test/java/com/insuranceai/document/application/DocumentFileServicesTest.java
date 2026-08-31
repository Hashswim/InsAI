package com.insuranceai.document.application;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.document.domain.StoredFile;
import com.insuranceai.document.domain.StoredFileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentFileServicesTest {

	@Mock StoragePort storage;
	@Mock OcrPort ocr;
	@Mock CaseParticipantGuard guard;
	@Mock StoredFileRepository files;

	@InjectMocks IssueUploadUrlService issueUploadUrlService;
	@InjectMocks ConfirmUploadService confirmUploadService;
	@InjectMocks IssueDownloadUrlService issueDownloadUrlService;
	@InjectMocks ListCaseFilesService listCaseFilesService;

	private final UUID caseId = UUID.randomUUID();
	private final UUID uploader = UUID.randomUUID();

	@Test
	@DisplayName("issue: 참여자 확인 후 <caseId>/<uuid>.<ext> 키와 업로드 URL 발급")
	void issueUploadUrl() {
		given(storage.uploadUrl(any())).willAnswer(i -> "/local-storage/" + i.getArgument(0));

		UploadTarget target = issueUploadUrlService.issue(new IssueUploadUrlCommand(
				caseId, null, uploader, "진단서.pdf", "application/pdf", 100));

		verify(guard).requireParticipant(caseId, uploader);
		assertThat(target.storageKey()).startsWith(caseId + "/").endsWith(".pdf");
		assertThat(target.url()).isEqualTo("/local-storage/" + target.storageKey());
	}

	@Test
	@DisplayName("issue: 비참여자면 FORBIDDEN 전파")
	void issueForbidden() {
		doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(guard).requireParticipant(any(), any());

		assertThatThrownBy(() -> issueUploadUrlService.issue(new IssueUploadUrlCommand(
				caseId, null, uploader, "a.pdf", "application/pdf", 100)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("issue: 허용되지 않는 확장자면 INVALID_INPUT")
	void issueBadExtension() {
		assertThatThrownBy(() -> issueUploadUrlService.issue(new IssueUploadUrlCommand(
				caseId, null, uploader, "a.exe", "x", 100)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("confirm: 메타데이터 저장 + OCR 텍스트 부착")
	void confirmWithOcr() {
		given(ocr.extractText("key")).willReturn("추출 텍스트");
		given(files.save(any())).willAnswer(i -> i.getArgument(0));

		UUID id = confirmUploadService.confirm(new ConfirmUploadCommand(
				caseId, null, uploader, "a.pdf", "application/pdf", 10, "key"));

		verify(guard).requireParticipant(caseId, uploader);
		verify(files).save(any(StoredFile.class));
		assertThat(id).isNotNull();
	}

	@Test
	@DisplayName("confirm: OCR 실패해도 파일은 저장(텍스트 없이)")
	void confirmOcrFailureSwallowed() {
		given(ocr.extractText(any())).willThrow(new RuntimeException("ocr down"));
		given(files.save(any())).willAnswer(i -> i.getArgument(0));

		UUID id = confirmUploadService.confirm(new ConfirmUploadCommand(
				caseId, null, uploader, "a.pdf", "application/pdf", 10, "key"));

		assertThat(id).isNotNull();
		verify(files).save(any(StoredFile.class));
	}

	@Test
	@DisplayName("download: 없는 파일 -> NOT_FOUND")
	void downloadNotFound() {
		given(files.findById(any())).willReturn(Optional.empty());

		assertThatThrownBy(() -> issueDownloadUrlService.issue(UUID.randomUUID(), uploader))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("download: 참여자면 다운로드 URL, 비참여자면 FORBIDDEN")
	void downloadUrl() {
		UUID fileId = UUID.randomUUID();
		StoredFile f = StoredFile.register(caseId, null, uploader, "a.pdf", "application/pdf", 10, "k/f.pdf");
		given(files.findById(fileId)).willReturn(Optional.of(f));
		given(storage.downloadUrl("k/f.pdf")).willReturn("/local-storage/k/f.pdf");

		assertThat(issueDownloadUrlService.issue(fileId, uploader)).isEqualTo("/local-storage/k/f.pdf");

		doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(guard).requireParticipant(eq(caseId), any());
		assertThatThrownBy(() -> issueDownloadUrlService.issue(fileId, UUID.randomUUID()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("list: byCase / bySubmission 를 뷰로 매핑")
	void list() {
		StoredFile f = StoredFile.register(caseId, UUID.randomUUID(), uploader, "a.pdf", "application/pdf", 10, "k");
		given(files.findByCaseIdOrderByCreatedAtAsc(caseId)).willReturn(List.of(f));
		given(files.findBySubmissionIdOrderByCreatedAtAsc(any())).willReturn(List.of(f));

		assertThat(listCaseFilesService.byCase(caseId)).singleElement()
				.extracting(StoredFileView::filename).isEqualTo("a.pdf");
		assertThat(listCaseFilesService.bySubmission(UUID.randomUUID())).hasSize(1);
	}
}
