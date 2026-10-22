package com.insuranceai.collab.api;

import java.util.UUID;

/**
 * collab 생성 계열 엔드포인트의 응답 본문.
 */
public final class CollabResponses {

	private CollabResponses() {
	}

	public record CaseCreated(UUID caseId) {
	}

	public record DocumentRequestCreated(UUID documentRequestId) {
	}

	public record MessageCreated(UUID messageId) {
	}

	public record DecisionCreated(UUID decisionId) {
	}
}
