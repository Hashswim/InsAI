package com.insuranceai.collab.api;

import com.insuranceai.collab.application.DocumentRequestAction;
import jakarta.validation.constraints.NotNull;

public record UpdateDocumentRequestRequest(
		@NotNull DocumentRequestAction action,
		String reason
) {
}
