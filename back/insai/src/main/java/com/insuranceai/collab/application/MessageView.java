package com.insuranceai.collab.application;

import com.insuranceai.auth.domain.Role;
import com.insuranceai.collab.domain.Message;

import java.time.Instant;
import java.util.UUID;

public record MessageView(
		UUID id,
		UUID senderId,
		Role senderRole,
		String body,
		boolean aiMediated,
		Instant createdAt
) {

	public static MessageView from(Message m) {
		return new MessageView(m.getId(), m.getSenderId(), m.getSenderRole(),
				m.getBody(), m.isAiMediated(), m.getCreatedAt());
	}
}
