package com.insuranceai.collab.domain;

/**
 * 케이스 진행 상태.
 * OPEN → DOCS_REQUESTED → DOCS_SUBMITTED → UNDER_REVIEW → DECISION_MADE → CLOSED
 * (추가 서류 요청 시 DOCS_REQUESTED 로 회귀 가능)
 */
public enum CaseStatus {
	OPEN,
	DOCS_REQUESTED,
	DOCS_SUBMITTED,
	UNDER_REVIEW,
	DECISION_MADE,
	CLOSED
}
