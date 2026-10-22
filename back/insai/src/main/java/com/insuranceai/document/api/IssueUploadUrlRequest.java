package com.insuranceai.document.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record IssueUploadUrlRequest(
		@NotNull UUID caseId,
		UUID submissionId,
		@NotBlank String filename,
		@NotBlank String contentType,
		@Positive long sizeBytes
) {
}
