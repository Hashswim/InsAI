package com.insuranceai.collab.api;

import jakarta.validation.constraints.NotBlank;

public record RegisterDecisionRequest(@NotBlank String rawText) {
}
