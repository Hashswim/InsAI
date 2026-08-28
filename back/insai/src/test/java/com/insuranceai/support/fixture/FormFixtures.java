package com.insuranceai.support.fixture;

import com.insuranceai.form.domain.FieldType;
import com.insuranceai.form.domain.FormField;
import com.insuranceai.form.domain.FormTemplate;

import java.util.List;

/**
 * form 도메인 테스트 픽스처.
 * 필수: hospital(TEXT), visitDate(DATE) / 선택: cost(NUMBER ≥0), category(SELECT 외래|입원)
 */
public final class FormFixtures {

	private FormFixtures() {
	}

	public static List<FormField> fields() {
		return List.of(
				new FormField("hospital", "병원명", FieldType.TEXT, true, null, 100, null, null),
				new FormField("visitDate", "내원일", FieldType.DATE, true, null, null, null, null),
				new FormField("cost", "치료비", FieldType.NUMBER, false, null, null, 0.0, null),
				new FormField("category", "구분", FieldType.SELECT, false, List.of("외래", "입원"), null, null, null)
		);
	}

	public static FormTemplate template() {
		return FormTemplate.create("medical-cert", "진단서", fields(), 1);
	}
}
