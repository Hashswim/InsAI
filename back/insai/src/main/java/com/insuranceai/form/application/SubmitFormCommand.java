package com.insuranceai.form.application;

import java.util.Map;
import java.util.UUID;

/**
 * documentRequestId 가 있으면 제출 성립 시 collab 서류 요청/케이스 상태를 전이시킨다.
 */
public record SubmitFormCommand(
		UUID documentRequestId,
		UUID caseId,
		UUID templateId,
		UUID submittedBy,
		Map<String, Object> data
) {
}
