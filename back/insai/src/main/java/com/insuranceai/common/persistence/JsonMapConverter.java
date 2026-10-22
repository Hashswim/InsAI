package com.insuranceai.common.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Map&lt;String,Object&gt; ↔ JSON 문자열 컬럼 변환.
 * TODO: PostgreSQL 로 완전 이관 시 jsonb + @JdbcTypeCode(SqlTypes.JSON) 로 교체 고려.
 */
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final TypeReference<LinkedHashMap<String, Object>> TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(Map<String, Object> attribute) {
		if (attribute == null) {
			return null;
		}
		try {
			return MAPPER.writeValueAsString(attribute);
		} catch (Exception e) {
			throw new IllegalArgumentException("JSON 직렬화 실패", e);
		}
	}

	@Override
	public Map<String, Object> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readValue(dbData, TYPE);
		} catch (Exception e) {
			throw new IllegalArgumentException("JSON 역직렬화 실패", e);
		}
	}
}
