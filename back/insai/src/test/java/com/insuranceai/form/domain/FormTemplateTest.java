package com.insuranceai.form.domain;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.support.fixture.FormFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormTemplateTest {

	@Test
	@DisplayName("create: 필드가 0개면 INVALID_INPUT")
	void noFields() {
		assertThatThrownBy(() -> FormTemplate.create("c", "n", List.of(), 1))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("create: 중복 필드 key 면 INVALID_INPUT")
	void duplicateKey() {
		FormField dup = new FormField("dup", "l", FieldType.TEXT, false, null, null, null, null);
		assertThatThrownBy(() -> FormTemplate.create("c", "n", List.of(dup, dup), 1))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("create: 정상 - fieldKeys 로 키 집합을 노출")
	void ok() {
		FormTemplate template = FormFixtures.template();

		assertThat(template.getVersion()).isEqualTo(1);
		assertThat(template.fieldKeys()).containsExactlyInAnyOrder("hospital", "visitDate", "cost", "category");
	}
}
