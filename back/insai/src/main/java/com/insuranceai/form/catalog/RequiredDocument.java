package com.insuranceai.form.catalog;

/**
 * 청구 유형별 요구 서류 항목.
 *
 * @param templateCode 대응하는 FormTemplate 의 code
 * @param name         서류명
 * @param condition    null 이면 필수, 값이 있으면 해당 조건일 때만 요구 (예: "재해(외인사)인 경우")
 */
public record RequiredDocument(String templateCode, String name, String condition) {

	public static RequiredDocument required(String templateCode, String name) {
		return new RequiredDocument(templateCode, name, null);
	}

	public static RequiredDocument conditional(String templateCode, String name, String condition) {
		return new RequiredDocument(templateCode, name, condition);
	}

	public boolean isMandatory() {
		return condition == null;
	}
}
