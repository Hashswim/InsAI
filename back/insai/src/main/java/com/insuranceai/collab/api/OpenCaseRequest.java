package com.insuranceai.collab.api;

import com.insuranceai.collab.domain.ClaimType;
import jakarta.validation.constraints.NotBlank;

public record OpenCaseRequest(@NotBlank String title, ClaimType claimType) {
}
