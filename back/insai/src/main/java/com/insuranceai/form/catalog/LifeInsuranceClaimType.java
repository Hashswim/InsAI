package com.insuranceai.form.catalog;

/**
 * 생명보험 청구 유형. 유형별로 접수 시 요구되는 서류 세트가 달라진다.
 */
public enum LifeInsuranceClaimType {

	DEATH("사망보험금"),
	DISABILITY("후유장해보험금"),
	DIAGNOSIS("진단보험금"),
	HOSPITALIZATION_SURGERY("입원·수술급여금");

	private final String label;

	LifeInsuranceClaimType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
