package com.insuranceai.form.api;

import com.insuranceai.common.web.ApiResponse;
import com.insuranceai.form.application.FormSubmissionView;
import com.insuranceai.form.application.GetSubmissionService;
import com.insuranceai.form.application.SaveDraftCommand;
import com.insuranceai.form.application.SaveDraftService;
import com.insuranceai.form.application.SubmitFormCommand;
import com.insuranceai.form.application.SubmitFormService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/submissions")
public class FormSubmissionController {

	private final SubmitFormService submitFormService;
	private final SaveDraftService saveDraftService;
	private final GetSubmissionService getSubmissionService;

	public FormSubmissionController(SubmitFormService submitFormService,
	                                SaveDraftService saveDraftService,
	                                GetSubmissionService getSubmissionService) {
		this.submitFormService = submitFormService;
		this.saveDraftService = saveDraftService;
		this.getSubmissionService = getSubmissionService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<FormResponses.SubmissionSaved> submit(
			@RequestHeader("X-User-Id") UUID userId,
			@Valid @RequestBody FormRequests.Submit request) {
		UUID id = submitFormService.submit(new SubmitFormCommand(
				request.documentRequestId(), request.caseId(), request.templateId(), userId, request.data()));
		return ApiResponse.ok(new FormResponses.SubmissionSaved(id));
	}

	@PostMapping("/draft")
	public ApiResponse<FormResponses.SubmissionSaved> saveDraft(
			@RequestHeader("X-User-Id") UUID userId,
			@Valid @RequestBody FormRequests.SaveDraft request) {
		UUID id = saveDraftService.saveDraft(new SaveDraftCommand(
				request.documentRequestId(), request.caseId(), request.templateId(), userId, request.data()));
		return ApiResponse.ok(new FormResponses.SubmissionSaved(id));
	}

	@PatchMapping("/{submissionId}")
	public ApiResponse<Void> updateDraft(
			@PathVariable UUID submissionId,
			@RequestBody FormRequests.UpdateDraft request) {
		saveDraftService.updateDraft(submissionId, request.data());
		return ApiResponse.ok();
	}

	@GetMapping("/{submissionId}")
	public ApiResponse<FormSubmissionView> get(@PathVariable UUID submissionId) {
		return ApiResponse.ok(getSubmissionService.get(submissionId));
	}

	/** GET /submissions?documentRequestId=... — 서류 요청에 대한 제출물 */
	@GetMapping(params = "documentRequestId")
	public ApiResponse<FormSubmissionView> getByDocumentRequest(
			@RequestParam UUID documentRequestId) {
		return ApiResponse.ok(getSubmissionService.getByDocumentRequestId(documentRequestId));
	}
}
