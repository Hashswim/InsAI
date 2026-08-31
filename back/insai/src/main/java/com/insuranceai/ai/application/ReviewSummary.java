package com.insuranceai.ai.application;

import java.util.List;

/**
 * 사정사 검토용 요약. highlights=핵심, issues=누락·불일치, needsAttention=사정사 확인 필요 여부.
 */
public record ReviewSummary(
		String summary,
		List<String> highlights,
		List<String> issues,
		boolean needsAttention
) {
}
