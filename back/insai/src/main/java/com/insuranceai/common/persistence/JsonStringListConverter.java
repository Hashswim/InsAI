package com.insuranceai.common.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * List&lt;String&gt; ↔ JSON 배열 문자열 컬럼 변환. null 은 빈 리스트로 읽는다.
 */
@Converter
public class JsonStringListConverter implements AttributeConverter<List<String>, String> {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final TypeReference<ArrayList<String>> TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(List<String> attribute) {
		try {
			return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
		} catch (Exception e) {
			throw new IllegalArgumentException("JSON 직렬화 실패", e);
		}
	}

	@Override
	public List<String> convertToEntityAttribute(String dbData) {
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
