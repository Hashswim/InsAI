package com.insuranceai.policy.domain;

/**
 * 생명보험 계열 상품 구분.
 */
public enum PolicyPlanType {

	WHOLE_LIFE("종신보험"),
	TERM_LIFE("정기보험"),
	CI("CI보험"),
	CANCER("암보험"),
	ANNUITY("연금보험"),
	ENDOWMENT("저축성보험"),
	OTHER("기타");

	private final String label;

	PolicyPlanType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
