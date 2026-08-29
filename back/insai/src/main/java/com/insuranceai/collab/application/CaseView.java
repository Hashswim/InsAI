package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;

import java.time.Instant;
import java.util.UUID;

/**
 * 케이스 목록/상세 조회용 읽기 모델.
 */
public record CaseView(
		UUID id,
		String title,
		String status,
		UUID claimantId,
		UUID adjusterId,
		Instant createdAt
) {

	public static CaseView from(Case c) {
		return new CaseView(c.getId(), c.getTitle(), c.getStatus().name(),
				c.getClaimantId(), c.getAdjusterId(), c.getCreatedAt());
	}
}
