package com.insuranceai.form.application;

import com.insuranceai.form.domain.FormField;

import java.util.List;
import java.util.UUID;

/**
 * 프론트가 HTML 폼을 렌더링하는 데 쓰는 템플릿 스키마.
 */
public record FormTemplateView(
		UUID id,
		String code,
		String name,
		int version,
		List<FormField> fields
) {
}
