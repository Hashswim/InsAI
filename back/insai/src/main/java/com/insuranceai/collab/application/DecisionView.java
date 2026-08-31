package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Decision;

import java.time.Instant;
import java.util.UUID;

/**
 * 사정 결과 조회용 읽기 모델. plainText 는 AI 평문 번역(후처리 전이면 null).
 */
public record DecisionView(
		String rawText,
		String plainText,
		UUID adjusterId,
		String adjusterName,
		Instant decidedAt
) {

	public static DecisionView from(Decision d, String adjusterName) {
		return new DecisionView(d.getRawText(), d.getPlainText(), d.getAdjusterId(), adjusterName, d.getCreatedAt());
	}
}
