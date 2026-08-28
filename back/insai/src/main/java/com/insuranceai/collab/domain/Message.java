package com.insuranceai.collab.domain;

import com.insuranceai.auth.domain.Role;
import com.insuranceai.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * 케이스 내 메시지. AI 중개 여부(aiMediated)를 기록한다.
 */
@Entity
@Table(name = "messages")
public class Message extends BaseEntity {

	@Column(nullable = false)
	private UUID caseId;

	@Column(nullable = false)
	private UUID senderId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role senderRole;

	@Column(nullable = false, length = 4000)
	private String body;

	@Column(nullable = false)
	private boolean aiMediated;

	protected Message() {
	}

	public static Message of(UUID caseId, UUID senderId, Role senderRole, String body) {
		Message message = new Message();
		message.assignId();
		message.caseId = Objects.requireNonNull(caseId, "caseId");
		message.senderId = Objects.requireNonNull(senderId, "senderId");
		message.senderRole = Objects.requireNonNull(senderRole, "senderRole");
		message.body = requireText(body, "내용");
		message.aiMediated = false;
		return message;
	}

	public UUID getCaseId() {
		return caseId;
	}

	public UUID getSenderId() {
		return senderId;
	}

	public Role getSenderRole() {
		return senderRole;
	}

	public String getBody() {
		return body;
	}

	public boolean isAiMediated() {
		return aiMediated;
	}
}
