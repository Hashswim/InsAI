package com.insuranceai.form.application;

import com.insuranceai.form.domain.FormTemplate;

import java.util.List;
import java.util.Map;

/**
 * 청구인 입력을 템플릿 칸 구조에 맞춰 구조화하는 AI 포트. 구현은 infrastructure (현재는 규칙 기반 스텁).
 */
public interface AiStructurePort {

	AiStructureResult structure(Map<String, Object> rawInput, FormTemplate template);

	record AiStructureResult(
			Map<String, Object> fields,
			Map<String, Double> confidence,
			List<String> missingFields
	) {
	}
}
