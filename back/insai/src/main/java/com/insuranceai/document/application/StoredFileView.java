package com.insuranceai.document.application;

import com.insuranceai.document.domain.StoredFile;

import java.time.Instant;
import java.util.UUID;

public record StoredFileView(
		UUID id,
		UUID caseId,
		UUID submissionId,
		String filename,
		String contentType,
		long sizeBytes,
		Instant createdAt
) {

	public static StoredFileView from(StoredFile f) {
		return new StoredFileView(f.getId(), f.getCaseId(), f.getSubmissionId(),
				f.getFilename(), f.getContentType(), f.getSizeBytes(), f.getCreatedAt());
	}
}
