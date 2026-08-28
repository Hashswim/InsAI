package com.insuranceai.collab.api;

import com.insuranceai.collab.application.AssignAdjusterService;
import com.insuranceai.collab.application.CreateDocumentRequestCommand;
import com.insuranceai.collab.application.CreateDocumentRequestService;
import com.insuranceai.collab.application.DocumentRequestView;
import com.insuranceai.collab.application.GetCaseTimelineService;
import com.insuranceai.collab.application.ListCaseDocumentRequestsService;
import com.insuranceai.collab.application.OpenCaseCommand;
import com.insuranceai.collab.application.OpenCaseService;
import com.insuranceai.collab.application.PostMessageCommand;
import com.insuranceai.collab.application.PostMessageService;
import com.insuranceai.collab.application.RegisterDecisionCommand;
import com.insuranceai.collab.application.RegisterDecisionService;
import com.insuranceai.collab.application.TimelineEntry;
import com.insuranceai.collab.application.UpdateDocumentRequestStatusService;
import com.insuranceai.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 청구 협업 공간 REST 엔드포인트. 행위자 식별은 게이트웨이가 주입하는 X-User-Id 헤더로 받는다.
 */
@RestController
public class CaseController {

	private final OpenCaseService openCaseService;
	private final AssignAdjusterService assignAdjusterService;
	private final CreateDocumentRequestService createDocumentRequestService;
	private final ListCaseDocumentRequestsService listCaseDocumentRequestsService;
	private final UpdateDocumentRequestStatusService updateDocumentRequestStatusService;
	private final PostMessageService postMessageService;
	private final RegisterDecisionService registerDecisionService;
	private final GetCaseTimelineService getCaseTimelineService;

	public CaseController(OpenCaseService openCaseService,
	                      AssignAdjusterService assignAdjusterService,
	                      CreateDocumentRequestService createDocumentRequestService,
	                      ListCaseDocumentRequestsService listCaseDocumentRequestsService,
	                      UpdateDocumentRequestStatusService updateDocumentRequestStatusService,
	                      PostMessageService postMessageService,
	                      RegisterDecisionService registerDecisionService,
	                      GetCaseTimelineService getCaseTimelineService) {
		this.openCaseService = openCaseService;
		this.assignAdjusterService = assignAdjusterService;
		this.createDocumentRequestService = createDocumentRequestService;
		this.listCaseDocumentRequestsService = listCaseDocumentRequestsService;
		this.updateDocumentRequestStatusService = updateDocumentRequestStatusService;
		this.postMessageService = postMessageService;
		this.registerDecisionService = registerDecisionService;
		this.getCaseTimelineService = getCaseTimelineService;
	}

	@PostMapping("/cases")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CollabResponses.CaseCreated> openCase(
			@RequestHeader("X-User-Id") UUID userId,
			@Valid @RequestBody OpenCaseRequest request) {
		UUID caseId = openCaseService.open(new OpenCaseCommand(userId, request.title()));
		return ApiResponse.ok(new CollabResponses.CaseCreated(caseId));
	}

	@PatchMapping("/cases/{caseId}/assign")
	public ApiResponse<Void> assignAdjuster(
			@PathVariable UUID caseId,
			@Valid @RequestBody AssignAdjusterRequest request) {
		assignAdjusterService.assign(caseId, request.adjusterId());
		return ApiResponse.ok();
	}

	@PostMapping("/cases/{caseId}/document-requests")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CollabResponses.DocumentRequestCreated> createDocumentRequest(
			@PathVariable UUID caseId,
			@RequestHeader("X-User-Id") UUID userId,
			@Valid @RequestBody CreateDocumentRequestRequest request) {
		UUID id = createDocumentRequestService.create(new CreateDocumentRequestCommand(
				caseId, userId, request.templateId(), request.title(), request.dueDate()));
		return ApiResponse.ok(new CollabResponses.DocumentRequestCreated(id));
	}

	@GetMapping("/cases/{caseId}/document-requests")
	public ApiResponse<List<DocumentRequestView>> listDocumentRequests(@PathVariable UUID caseId) {
		return ApiResponse.ok(listCaseDocumentRequestsService.list(caseId));
	}

	@PatchMapping("/document-requests/{documentRequestId}")
	public ApiResponse<Void> updateDocumentRequest(
			@PathVariable UUID documentRequestId,
			@Valid @RequestBody UpdateDocumentRequestRequest request) {
		updateDocumentRequestStatusService.updateStatus(
				documentRequestId, request.action(), request.reason());
		return ApiResponse.ok();
	}

	@PostMapping("/cases/{caseId}/messages")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CollabResponses.MessageCreated> postMessage(
			@PathVariable UUID caseId,
			@RequestHeader("X-User-Id") UUID userId,
			@Valid @RequestBody PostMessageRequest request) {
		UUID id = postMessageService.post(new PostMessageCommand(
				caseId, userId, request.senderRole(), request.body()));
		return ApiResponse.ok(new CollabResponses.MessageCreated(id));
	}

	@PostMapping("/cases/{caseId}/decisions")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CollabResponses.DecisionCreated> registerDecision(
			@PathVariable UUID caseId,
			@RequestHeader("X-User-Id") UUID userId,
			@Valid @RequestBody RegisterDecisionRequest request) {
		UUID id = registerDecisionService.register(new RegisterDecisionCommand(
				caseId, userId, request.rawText()));
		return ApiResponse.ok(new CollabResponses.DecisionCreated(id));
	}

	@GetMapping("/cases/{caseId}/timeline")
	public ApiResponse<List<TimelineEntry>> timeline(@PathVariable UUID caseId) {
		return ApiResponse.ok(getCaseTimelineService.timeline(caseId));
	}
}
