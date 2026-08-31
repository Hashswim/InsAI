package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.ClaimType;

import java.util.UUID;

/** claimType 이 null 이면 OTHER 로 간주한다. */
public record OpenCaseCommand(UUID claimantId, String title, ClaimType claimType) {
}
