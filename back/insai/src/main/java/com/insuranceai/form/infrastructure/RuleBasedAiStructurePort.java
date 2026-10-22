package com.insuranceai.form.infrastructure;

import com.insuranceai.form.application.AiStructurePort;
import com.insuranceai.form.domain.FormField;
import com.insuranceai.form.domain.FormTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 규칙 기반 스텁 구현. 실제 LLM 연동 전까지 계약(응답 형태)만 만족시킨다.
 * - 입력값을 그대로 fields 로 반환하고 confidence 는 1.0
 * - 필수인데 값이 없는 필드를 missingFields 로 계산
 * TODO: ai 도메인의 LLM 오케스트레이션으로 교체.
 */
@Component
public class RuleBasedAiStructurePort implements AiStructurePort {

	@Override
	public AiStructureResult structure(Map<String, Object> rawInput, FormTemplate template) {
		Map<String, Object> input = rawInput == null ? Map.of() : rawInput;

		Map<String, Object> fields = new LinkedHashMap<>();
		Map<String, Double> confidence = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();

		for (FormField field : template.getFields()) {
			Object value = input.get(field.key());
			boolean blank = value == null || (value instanceof String s && s.isBlank());
			if (blank) {
				if (field.required()) {
					missing.add(field.key());
				}
			} else {
				fields.put(field.key(), value);
				confidence.put(field.key(), 1.0);
			}
		}
		return new AiStructureResult(fields, confidence, missing);
	}
}
