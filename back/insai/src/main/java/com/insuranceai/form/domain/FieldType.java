package com.insuranceai.form.domain;

/**
 * 양식 필드 타입.
 * TEXT   - 자유 텍스트 (maxLength 검증)
 * NUMBER - 숫자 (min/max 검증)
 * DATE   - 날짜 (YYYY-MM-DD)
 * SELECT - 목록 선택 (options 내 값)
 * FILE   - 첨부 파일 참조
 */
public enum FieldType {
	TEXT,
	NUMBER,
	DATE,
	SELECT,
	FILE
}
