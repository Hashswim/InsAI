package com.insuranceai.form.application;

import java.util.Map;
import java.util.UUID;

public record SaveDraftCommand(
		UUID documentRequestId,
		UUID caseId,
		UUID templateId,
		UUID submittedBy,
		Map<String, Object> data
) {
}
