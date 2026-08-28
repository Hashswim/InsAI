package com.insuranceai.form.api;

import java.util.UUID;

public final class FormResponses {

	private FormResponses() {
	}

	public record TemplateCreated(UUID templateId) {
	}

	public record SubmissionSaved(UUID submissionId) {
	}
}
