package com.insuranceai.collab.domain;

public enum DocumentRequestStatus {
	PENDING,
	SUBMITTED,
	ACCEPTED,
	REJECTED,
	/** 보완 요청: 사유를 받아 청구인이 수정 후 재제출한다. */
	SUPPLEMENT_REQUESTED
}
