package com.insuranceai.collab.application;

import java.util.UUID;

public record RegisterDecisionCommand(UUID caseId, UUID adjusterId, String rawText) {
}
