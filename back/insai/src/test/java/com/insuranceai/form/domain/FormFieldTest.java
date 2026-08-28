package com.insuranceai.form.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormFieldTest {

	@Test
	@DisplayName("TEXT: maxLength 초과면 사유 반환")
	void text() {
		FormField f = new FormField("k", "l", FieldType.TEXT, true, null, 3, null, null);
		assertThat(f.validate("abc")).isNull();
		assertThat(f.validate("abcd")).isNotNull();
	}

	@Test
	@DisplayName("NUMBER: 비숫자/범위 밖이면 사유 반환")
	void number() {
		FormField f = new FormField("k", "l", FieldType.NUMBER, true, null, null, 0.0, 100.0);
		assertThat(f.validate("50")).isNull();
		assertThat(f.validate("공짜")).contains("숫자");
		assertThat(f.validate("-1")).isNotNull();
		assertThat(f.validate("101")).isNotNull();
	}

	@Test
	@DisplayName("DATE: YYYY-MM-DD 아니면 사유 반환")
	void date() {
		FormField f = new FormField("k", "l", FieldType.DATE, true, null, null, null, null);
		assertThat(f.validate("2026-01-15")).isNull();
		assertThat(f.validate("2026/01/15")).isNotNull();
	}

	@Test
	@DisplayName("SELECT: options 밖 값이면 사유 반환")
	void select() {
		FormField f = new FormField("k", "l", FieldType.SELECT, true, List.of("외래", "입원"), null, null, null);
		assertThat(f.validate("외래")).isNull();
		assertThat(f.validate("응급")).isNotNull();
	}
}
