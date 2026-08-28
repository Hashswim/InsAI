package com.insuranceai.collab.api;

import jakarta.validation.constraints.NotBlank;

public record OpenCaseRequest(@NotBlank String title) {
}
