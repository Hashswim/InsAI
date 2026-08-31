package com.insuranceai.ai.api;

import com.insuranceai.ai.application.ReviewSummary;
import com.insuranceai.ai.application.ReviewSummaryService;
import com.insuranceai.common.web.ApiResponse;
import com.insuranceai.form.application.FormSubmissionView;
import com.insuranceai.form.application.GetSubmissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ai")
public class AiController {

	private final ReviewSummaryService reviewSummaryService;
	private final GetSubmissionService getSubmissionService;

	public AiController(ReviewSummaryService reviewSummaryService,
	                   GetSubmissionService getSubmissionService) {
		this.reviewSummaryService = reviewSummaryService;
		this.getSubmissionService = getSubmissionService;
	}

	public record ReviewSummaryRequest(@NotNull UUID submissionId) {
	}

	/** 사정사 검토용 요약. AI 미연동 시 스텁 응답(needsAttention=true). */
	@PostMapping("/review-summary")
	public ApiResponse<ReviewSummary> reviewSummary(@Valid @RequestBody ReviewSummaryRequest request) {
		FormSubmissionView submission = getSubmissionService.get(request.submissionId());
		return ApiResponse.ok(reviewSummaryService.summarize(submission.data(), List.of()));
	}
}
