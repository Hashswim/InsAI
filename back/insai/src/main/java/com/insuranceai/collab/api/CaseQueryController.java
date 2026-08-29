package com.insuranceai.collab.api;

import com.insuranceai.collab.application.CaseView;
import com.insuranceai.collab.application.GetCaseService;
import com.insuranceai.collab.application.ListCasesService;
import com.insuranceai.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 케이스 조회 (목록/상세). 명령은 CaseController.
 */
@RestController
public class CaseQueryController {

	private final ListCasesService listCasesService;
	private final GetCaseService getCaseService;

	public CaseQueryController(ListCasesService listCasesService, GetCaseService getCaseService) {
		this.listCasesService = listCasesService;
		this.getCaseService = getCaseService;
	}

	@GetMapping("/cases")
	public ApiResponse<List<CaseView>> listCases(
			@RequestHeader("X-User-Id") UUID userId,
			@RequestParam(name = "unassigned", defaultValue = "false") boolean unassigned) {
		return ApiResponse.ok(unassigned
				? listCasesService.unassigned()
				: listCasesService.forUser(userId));
	}

	@GetMapping("/cases/{caseId}")
	public ApiResponse<CaseView> getCase(@PathVariable UUID caseId) {
		return ApiResponse.ok(getCaseService.get(caseId));
	}
}
