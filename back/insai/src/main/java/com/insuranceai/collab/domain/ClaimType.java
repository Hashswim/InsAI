package com.insuranceai.collab.domain;

/**
 * 청구 보험 분야. 케이스 개설 시 청구인이 선택한다.
 */
public enum ClaimType {

	DEATH("사망보험금"),
	DISABILITY("후유장해보험금"),
	DIAGNOSIS("진단보험금"),
	HOSPITALIZATION_SURGERY("입원·수술급여금"),
	OTHER("기타");

	private final String label;

	ClaimType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
