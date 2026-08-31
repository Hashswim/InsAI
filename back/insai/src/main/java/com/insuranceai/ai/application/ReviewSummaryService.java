package com.insuranceai.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.ai.application.LlmClient.LlmRequest;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 제출물(입력값 + 첨부 OCR 텍스트)을 사정사용 요약으로 정리한다.
 */
@Service
public class ReviewSummaryService {

	private static final String SYSTEM = """
			너는 보험 청구 서류를 검토하는 손해사정 보조자다.
			제출된 값과 첨부 문서 텍스트를 근거로만 판단하고, 없는 사실을 지어내지 마라.
			반드시 아래 JSON 스키마로만 답하라(설명·코드펜스 금지):
			{"summary": string, "highlights": string[], "issues": string[], "needsAttention": boolean}
			- summary: 3문장 이내 핵심 요약
			- highlights: 지급 판단에 중요한 항목
			- issues: 누락·불일치·확인이 필요한 항목
			- needsAttention: 사정사의 직접 확인이 필요하면 true
			""";

	private final LlmClient llmClient;
	private final ObjectMapper mapper = new ObjectMapper();

	public ReviewSummaryService(LlmClient llmClient) {
		this.llmClient = llmClient;
	}

	public ReviewSummary summarize(Map<String, Object> submittedData, List<String> attachmentTexts) {
		String userPrompt = buildPrompt(submittedData, attachmentTexts);

		String raw;
		try {
			raw = llmClient.complete(new LlmRequest("review-summary", SYSTEM, userPrompt));
		} catch (RuntimeException e) {
			throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 요약 생성에 실패했습니다.");
		}

		try {
			return mapper.readValue(extractJson(raw), ReviewSummary.class);
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 응답을 해석하지 못했습니다.");
		}
	}

	private static String buildPrompt(Map<String, Object> data, List<String> texts) {
		StringBuilder sb = new StringBuilder("[제출된 값]\n");
		(data == null ? Map.<String, Object>of() : data)
				.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
		if (texts != null && !texts.isEmpty()) {
			sb.append("\n[첨부 문서 텍스트]\n");
			texts.forEach(t -> sb.append(t).append("\n---\n"));
		}
		return sb.toString();
	}

	/** 코드펜스/서두를 걷어내고 첫 '{' ~ 마지막 '}' 만 남긴다. */
	private static String extractJson(String raw) {
		if (raw == null) {
			return "{}";
		}
		int start = raw.indexOf('{');
		int end = raw.lastIndexOf('}');
		return (start >= 0 && end > start) ? raw.substring(start, end + 1) : raw;
	}
}
