package com.insuranceai.form.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.form.domain.FormField;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * List&lt;FormField&gt; ↔ JSON 문자열 컬럼 변환.
 */
@Converter
public class FormFieldListConverter implements AttributeConverter<List<FormField>, String> {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final TypeReference<ArrayList<FormField>> TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(List<FormField> attribute) {
		try {
			return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
		} catch (Exception e) {
			throw new IllegalArgumentException("JSON 직렬화 실패", e);
		}
	}

	@Override
	public List<FormField> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return new ArrayList<>();
		}
		try {
			return MAPPER.readValue(dbData, TYPE);
		} catch (Exception e) {
			throw new IllegalArgumentException("JSON 역직렬화 실패", e);
		}
	}
}
