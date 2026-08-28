package com.insuranceai.collab.application;

import java.time.LocalDate;
import java.util.UUID;

public record CreateDocumentRequestCommand(
		UUID caseId,
		UUID requesterId,
		UUID templateId,
		String title,
		LocalDate dueDate
) {
}
