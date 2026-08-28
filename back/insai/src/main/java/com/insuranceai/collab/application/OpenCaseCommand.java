package com.insuranceai.collab.application;

import java.util.UUID;

public record OpenCaseCommand(UUID claimantId, String title) {
}
