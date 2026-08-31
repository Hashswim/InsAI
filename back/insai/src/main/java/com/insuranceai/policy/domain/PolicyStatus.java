package com.insuranceai.policy.domain;

/**
 * 보험 계약 상태.
 */
public enum PolicyStatus {

	ACTIVE("정상"),
	LAPSED("실효"),
	EXPIRED("만기"),
	TERMINATED("해지");

	private final String label;

	PolicyStatus(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
