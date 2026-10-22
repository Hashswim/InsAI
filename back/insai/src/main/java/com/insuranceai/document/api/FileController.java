package com.insuranceai.document.api;

import com.insuranceai.auth.application.AuthenticateAccessTokenService;
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
 * 업로드에 청구 단위 JWT(Authorization)가 오면 토큰의 계정·케이스가 요청과 같아야 한다.
 */
@RestController
public class FileController {

	private final IssueUploadUrlService issueUploadUrlService;
	private final ConfirmUploadService confirmUploadService;
	private final IssueDownloadUrlService issueDownloadUrlService;
	private final ListCaseFilesService listCaseFilesService;
	private final AuthenticateAccessTokenService authentication;

	public FileController(IssueUploadUrlService issueUploadUrlService,
	                      ConfirmUploadService confirmUploadService,
	                      IssueDownloadUrlService issueDownloadUrlService,
	                      ListCaseFilesService listCaseFilesService,
	                      AuthenticateAccessTokenService authentication) {
		this.issueUploadUrlService = issueUploadUrlService;
		this.confirmUploadService = confirmUploadService;
		this.issueDownloadUrlService = issueDownloadUrlService;
		this.listCaseFilesService = listCaseFilesService;
		this.authentication = authentication;
	}

	@PostMapping("/files/upload-url")
	public ApiResponse<DocumentResponses.UploadUrlIssued> issueUploadUrl(
			@RequestHeader("X-User-Id") UUID userId,
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@Valid @RequestBody IssueUploadUrlRequest request) {
		requireClaimScope(authorization, userId, request.caseId());
		UploadTarget target = issueUploadUrlService.issue(new IssueUploadUrlCommand(
				request.caseId(), request.submissionId(), userId,
				request.filename(), request.contentType(), request.sizeBytes()));
		return ApiResponse.ok(new DocumentResponses.UploadUrlIssued(target.url(), target.storageKey()));
	}

	@PostMapping("/files")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<DocumentResponses.FileStored> confirmUpload(
			@RequestHeader("X-User-Id") UUID userId,
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@Valid @RequestBody ConfirmUploadRequest request) {
		requireClaimScope(authorization, userId, request.caseId());
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

	// ponytail: 토큰이 없으면 기존 X-User-Id 헤더를 그대로 믿는다. 프론트가 청구 단위 토큰을 보내면 필수로 바꾼다.
	private void requireClaimScope(String authorization, UUID userId, UUID caseId) {
		if (authorization == null) return;
		if (!authorization.startsWith("Bearer ")) throw new BusinessException(ErrorCode.UNAUTHORIZED);
		var actor = authentication.authenticate(authorization.substring("Bearer ".length()));
		if (!actor.userId().equals(userId)) throw new BusinessException(ErrorCode.UNAUTHORIZED);
		actor.requireCase(caseId);
	}
}
