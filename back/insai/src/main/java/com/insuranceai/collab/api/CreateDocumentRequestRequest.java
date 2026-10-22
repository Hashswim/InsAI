package com.insuranceai.collab.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateDocumentRequestRequest(
		@NotNull UUID templateId,
		@NotBlank String title,
		LocalDate dueDate
) {
}
