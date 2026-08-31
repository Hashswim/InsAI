package com.insuranceai.document.application;

import java.util.UUID;

public record ConfirmUploadCommand(
		UUID caseId,
		UUID submissionId,
		UUID uploaderId,
		String filename,
		String contentType,
		long sizeBytes,
		String storageKey
) {
}
