package com.insuranceai.document.api;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.common.web.ApiResponse;
import com.insuranceai.document.application.ConfirmUploadCommand;
import com.insuranceai.document.application.ConfirmUploadService;
import com.insuranceai.document.application.IssueUploadUrlCommand;
import com.insuranceai.document.application.IssueUploadUrlService;
import com.insuranceai.document.application.IssueDownloadUrlService;
import com.insuranceai.document.application.ListCaseFilesService;
import com.insuranceai.document.application.StoredFileView;
import com.insuranceai.document.application.UploadTarget;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 파일 첨부 REST. 업로드는 2단계: (1) upload-url 발급 → 클라이언트가 그 URL 로 PUT →
 * (2) /files 로 확정. 다운로드도 URL 발급 방식.
 */
@RestController
public class FileController {

	private final IssueUploadUrlService issueUploadUrlService;
	private final ConfirmUploadService confirmUploadService;
	private final IssueDownloadUrlService issueDownloadUrlService;
	private final ListCaseFilesService listCaseFilesService;

	public FileController(IssueUploadUrlService issueUploadUrlService,
	                      ConfirmUploadService confirmUploadService,
	                      IssueDownloadUrlService issueDownloadUrlService,
	                      ListCaseFilesService listCaseFilesService) {
		this.issueUploadUrlService = issueUploadUrlService;
		this.confirmUploadService = confirmUploadService;
		this.issueDownloadUrlService = issueDownloadUrlService;
		this.listCaseFilesService = listCaseFilesService;
	}

	@PostMapping("/files/upload-url")
	public ApiResponse<DocumentResponses.UploadUrlIssued> issueUploadUrl(
			@RequestHeader("X-User-Id") UUID userId,
			@Valid @RequestBody IssueUploadUrlRequest request) {
		UploadTarget target = issueUploadUrlService.issue(new IssueUploadUrlCommand(
				request.caseId(), request.submissionId(), userId,
				request.filename(), request.contentType(), request.sizeBytes()));
		return ApiResponse.ok(new DocumentResponses.UploadUrlIssued(target.url(), target.storageKey()));
	}

	@PostMapping("/files")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<DocumentResponses.FileStored> confirmUpload(
			@RequestHeader("X-User-Id") UUID userId,
			@Valid @RequestBody ConfirmUploadRequest request) {
		UUID fileId = confirmUploadService.confirm(new ConfirmUploadCommand(
				request.caseId(), request.submissionId(), userId,
				request.filename(), request.contentType(), request.sizeBytes(), request.storageKey()));
		return ApiResponse.ok(new DocumentResponses.FileStored(fileId));
	}

	@GetMapping("/files/{fileId}/download-url")
	public ApiResponse<DocumentResponses.DownloadUrl> downloadUrl(
			@PathVariable UUID fileId,
			@RequestHeader("X-User-Id") UUID userId) {
		return ApiResponse.ok(new DocumentResponses.DownloadUrl(
				issueDownloadUrlService.issue(fileId, userId)));
	}

	@GetMapping("/files")
	public ApiResponse<List<StoredFileView>> list(
			@RequestParam(required = false) UUID caseId,
			@RequestParam(required = false) UUID submissionId) {
		if (submissionId != null) {
			return ApiResponse.ok(listCaseFilesService.bySubmission(submissionId));
		}
		if (caseId != null) {
			return ApiResponse.ok(listCaseFilesService.byCase(caseId));
		}
		throw new BusinessException(ErrorCode.INVALID_INPUT, "caseId 또는 submissionId 가 필요합니다.");
	}
}
