package com.insuranceai.form.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.form.application.AiStructurePort;
import com.insuranceai.form.domain.FormField;
import com.insuranceai.form.domain.FormTemplate;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI ChatClient로 청구인 입력을 템플릿 칸에 맞춰 구조화한다.
 * - 청구인이 직접 입력한 값이 우선이고 confidence 1.0, LLM이 채운 값은 0.7
 * - 템플릿에 없는 키는 버린다
 * - LLM 호출이 실패하면 규칙 기반 결과로 돌아간다
 */
public class SpringAiStructurePort implements AiStructurePort {

	private static final String SYSTEM = """
			너는 보험금 청구 양식 구조화기다. 청구인 입력에서 각 칸의 값을 찾아 JSON 객체 하나로만 답한다.
			키는 주어진 칸의 key만 쓴다. 입력에 근거가 없는 값은 만들지 말고 키를 생략한다.
			날짜는 YYYY-MM-DD, 숫자는 숫자로 쓴다.""";
	// ponytail: LLM 값의 신뢰도는 고정값. 칸별 근거가 필요해지면 응답에 confidence를 함께 받는다.
	private static final double LLM_CONFIDENCE = 0.7;

	private final ChatClient chat;
	private final AiStructurePort fallback;
	private final ObjectMapper mapper;

	public SpringAiStructurePort(ChatClient chat, AiStructurePort fallback, ObjectMapper mapper) {
		this.chat = chat;
		this.fallback = fallback;
		this.mapper = mapper;
	}

	@Override
	public AiStructureResult structure(Map<String, Object> rawInput, FormTemplate template) {
		Map<String, Object> input = rawInput == null ? Map.of() : rawInput;
		Map<String, Object> extracted;
		try {
			extracted = chat.prompt()
					.system(SYSTEM)
					.user(userMessage(input, template))
					.call()
					.entity(new ParameterizedTypeReference<Map<String, Object>>() {});
		} catch (RuntimeException e) {
			return fallback.structure(rawInput, template);
		}
		if (extracted == null) extracted = Map.of();

		Map<String, Object> fields = new LinkedHashMap<>();
		Map<String, Double> confidence = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		for (FormField field : template.getFields()) {
			Object typed = input.get(field.key());
			Object inferred = extracted.get(field.key());
			if (!isBlank(typed)) {
				fields.put(field.key(), typed);
				confidence.put(field.key(), 1.0);
			} else if (!isBlank(inferred)) {
				fields.put(field.key(), inferred);
				confidence.put(field.key(), LLM_CONFIDENCE);
			} else if (field.required()) {
				missing.add(field.key());
			}
		}
		return new AiStructureResult(fields, confidence, missing);
	}

	private String userMessage(Map<String, Object> input, FormTemplate template) {
		List<Map<String, Object>> fields = template.getFields().stream()
				.map(f -> Map.<String, Object>of("key", f.key(), "label", f.label(), "type", f.type().name()))
				.toList();
		try {
			return "칸: " + mapper.writeValueAsString(fields) + "\n입력: " + mapper.writeValueAsString(input);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("입력을 직렬화할 수 없습니다.", e);
		}
	}

	private static boolean isBlank(Object value) {
		return value == null || (value instanceof String s && s.isBlank());
	}
}
