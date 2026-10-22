package com.insuranceai.collab.application;

import com.insuranceai.auth.domain.Role;

import java.util.UUID;

public record PostMessageCommand(UUID caseId, UUID senderId, Role senderRole, String body) {
}
