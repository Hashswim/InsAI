package com.insuranceai.collab.api;

import com.insuranceai.auth.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostMessageRequest(
		@NotNull Role senderRole,
		@NotBlank String body
) {
}
