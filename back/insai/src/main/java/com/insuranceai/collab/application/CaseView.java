package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.ClaimType;

import java.time.Instant;
import java.util.UUID;

/**
 * 케이스 목록/상세 조회용 읽기 모델.
 */
public record CaseView(
		UUID id,
		String title,
		String status,
		String claimType,
		String claimTypeLabel,
		UUID claimantId,
		String claimantName,
		UUID adjusterId,
		String adjusterName,
		Instant createdAt
) {

	public static CaseView from(Case c, String claimantName, String adjusterName) {
		ClaimType ct = c.getClaimType();
		return new CaseView(
				c.getId(), c.getTitle(), c.getStatus().name(),
				ct == null ? null : ct.name(),
				ct == null ? null : ct.label(),
				c.getClaimantId(), claimantName,
				c.getAdjusterId(), adjusterName,
				c.getCreatedAt());
	}
}
