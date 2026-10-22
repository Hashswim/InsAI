package com.insuranceai.form.domain;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 템플릿의 "칸" 하나. JSON 으로 직렬화되어 FormTemplate 에 저장된다.
 * options/maxLength/min/max 는 타입별로만 의미가 있으며 null 허용.
 */
public record FormField(
		String key,
		String label,
		FieldType type,
		boolean required,
		List<String> options,
		Integer maxLength,
		Double min,
		Double max
) {

	/** 값이 유효하면 null, 아니면 사유 문자열. blank 값은 호출 측에서 걸러 넣는다. */
	public String validate(Object value) {
		String text = String.valueOf(value);
		return switch (type) {
			case TEXT -> (maxLength != null && text.length() > maxLength)
					? "최대 " + maxLength + "자까지 입력할 수 있습니다" : null;
			case NUMBER -> validateNumber(text);
			case DATE -> isIsoDate(text) ? null : "날짜 형식(YYYY-MM-DD)이어야 합니다";
			case SELECT -> (options != null && options.contains(text)) ? null : "허용되지 않은 선택값입니다";
			case FILE -> null;
		};
	}

	private String validateNumber(String text) {
		double number;
		try {
			number = Double.parseDouble(text);
		} catch (NumberFormatException e) {
			return "숫자여야 합니다";
		}
		if (min != null && number < min) {
			return min + " 이상이어야 합니다";
		}
		if (max != null && number > max) {
			return max + " 이하여야 합니다";
		}
		return null;
	}

	private static boolean isIsoDate(String text) {
		try {
			LocalDate.parse(text);
			return true;
		} catch (DateTimeParseException e) {
			return false;
		}
	}
}
