package com.insuranceai.form.api;

import com.insuranceai.form.domain.FormField;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * form API 요청 본문 모음.
 */
public final class FormRequests {

	private FormRequests() {
	}

	public record CreateTemplate(
			@NotBlank String code,
			@NotBlank String name,
			@NotEmpty List<FormField> fields
	) {
	}

	public record SaveDraft(
			UUID documentRequestId,
			@NotNull UUID caseId,
			@NotNull UUID templateId,
			Map<String, Object> data
	) {
	}

	public record UpdateDraft(
			Map<String, Object> data
	) {
	}

	public record Submit(
			UUID documentRequestId,
			@NotNull UUID caseId,
			@NotNull UUID templateId,
			@NotNull Map<String, Object> data
	) {
	}
}
