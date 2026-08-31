package com.insuranceai.ai.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.ai.application.LlmClient;
import com.insuranceai.ai.application.LlmClient.LlmRequest;
import com.insuranceai.form.application.AiStructurePort;
import com.insuranceai.form.domain.FormField;
import com.insuranceai.form.domain.FormTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * insai.ai.enabled=true 일 때 form 의 AiStructurePort 를 LLM 기반으로 대체(@Primary).
 * 실패하면 규칙 기반 결과로 폴백해 제출을 막지 않는다.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "insai.ai", name = "enabled", havingValue = "true")
public class LlmAiStructurePort implements AiStructurePort {

	private static final Logger log = LoggerFactory.getLogger(LlmAiStructurePort.class);

	private static final String SYSTEM = """
			너는 보험 청구인의 자유 입력을 정해진 양식 칸(key)에 맞춰 정리하는 보조자다.
			주어진 입력에 근거해서만 값을 채우고, 없는 값은 비워라(지어내지 마라).
			아래 JSON 으로만 답하라(설명·코드펜스 금지):
			{"fields": {key: value}, "confidence": {key: 0~1}, "missingFields": [key]}
			""";

	private final LlmClient llm;
	private final ObjectMapper mapper = new ObjectMapper();

	public LlmAiStructurePort(LlmClient llm) {
		this.llm = llm;
	}

	@Override
	public AiStructureResult structure(Map<String, Object> rawInput, FormTemplate template) {
		Map<String, Object> input = rawInput == null ? Map.of() : rawInput;
		try {
			String raw = llm.complete(new LlmRequest("structure", SYSTEM, buildPrompt(input, template)));
			Parsed p = mapper.readValue(extractJson(raw), Parsed.class);
			return new AiStructureResult(
					p.fields == null ? Map.of() : p.fields,
					p.confidence == null ? Map.of() : p.confidence,
					p.missingFields == null ? List.of() : p.missingFields);
		} catch (Exception e) {
			log.warn("LLM 구조화 실패 — 규칙 기반으로 폴백", e);
			return fallback(input, template);
		}
	}

	private static AiStructureResult fallback(Map<String, Object> input, FormTemplate template) {
		Map<String, Object> fields = new LinkedHashMap<>();
		Map<String, Double> confidence = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		for (FormField f : template.getFields()) {
			Object v = input.get(f.key());
			boolean blank = v == null || (v instanceof String s && s.isBlank());
			if (blank) {
				if (f.required()) {
					missing.add(f.key());
				}
			} else {
				fields.put(f.key(), v);
				confidence.put(f.key(), 1.0);
			}
		}
		return new AiStructureResult(fields, confidence, missing);
	}

	private static String buildPrompt(Map<String, Object> input, FormTemplate template) {
		StringBuilder sb = new StringBuilder("[양식 칸]\n");
		for (FormField f : template.getFields()) {
			sb.append("- ").append(f.key()).append(" (").append(f.label())
					.append(f.required() ? ", 필수" : "").append(")\n");
		}
		sb.append("\n[청구인 입력]\n");
		input.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
		return sb.toString();
	}

	private static String extractJson(String raw) {
		if (raw == null) {
			return "{}";
		}
		int a = raw.indexOf('{');
		int b = raw.lastIndexOf('}');
		return (a >= 0 && b > a) ? raw.substring(a, b + 1) : raw;
	}

	private static final class Parsed {
		public Map<String, Object> fields;
		public Map<String, Double> confidence;
		public List<String> missingFields;
	}
}
