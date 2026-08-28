package com.insuranceai.form.application;

import com.insuranceai.form.domain.FormSubmission;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 사정사 검토 화면용. 사용자 입력 + AI 구조화 결과 + 누락/검증 결과.
 */
public record FormSubmissionView(
		UUID id,
		UUID documentRequestId,
		UUID caseId,
		UUID templateId,
		String status,
		int version,
		boolean complete,
		Map<String, Object> data,
		Map<String, Object> aiStructured,
		List<String> missingFields,
		List<String> validationErrors
) {

	public static FormSubmissionView from(FormSubmission s) {
		return new FormSubmissionView(
				s.getId(), s.getDocumentRequestId(), s.getCaseId(), s.getTemplateId(),
				s.getStatus().name(), s.getVersion(), s.isComplete(),
				s.getData(), s.getAiStructured(), s.getMissingFields(), s.getValidationErrors());
	}
}
