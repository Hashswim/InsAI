package com.insuranceai.collab.domain;

import com.insuranceai.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 청구 협업 공간(케이스) 애그리게이트 루트.
 *
 * 참여자는 claimantId(청구인) 와 adjusterId(배정된 손해사정사) 로 표현한다.
 * 상태 전이는 전용 메서드로만 하며, 허용되지 않은 전이는 BusinessException(CONFLICT).
 */
@Entity
@Table(name = "cases")
public class Case extends BaseEntity {

	@Column(nullable = false)
	private UUID claimantId;

	@Column
	private UUID adjusterId;

	@Column(nullable = false)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CaseStatus status;

	protected Case() {
	}

	public static Case open(UUID claimantId, String title) {
		throw new UnsupportedOperationException("TODO: Case.open");
	}

	public void assignAdjuster(UUID adjusterId) {
		throw new UnsupportedOperationException("TODO: Case.assignAdjuster");
	}

	public void markDocsRequested() {
		throw new UnsupportedOperationException("TODO: Case.markDocsRequested");
	}

	public void markDocsSubmitted() {
		throw new UnsupportedOperationException("TODO: Case.markDocsSubmitted");
	}

	public void startReview() {
		throw new UnsupportedOperationException("TODO: Case.startReview");
	}

	public void recordDecision() {
		throw new UnsupportedOperationException("TODO: Case.recordDecision");
	}

	public void close() {
		throw new UnsupportedOperationException("TODO: Case.close");
	}

	/** userId 가 청구인도 배정 사정사도 아니면 BusinessException(FORBIDDEN) */
	public void requireParticipant(UUID userId) {
		throw new UnsupportedOperationException("TODO: Case.requireParticipant");
	}

	public UUID getClaimantId() {
		return claimantId;
	}

	public UUID getAdjusterId() {
		return adjusterId;
	}

	public String getTitle() {
		return title;
	}

	public CaseStatus getStatus() {
		return status;
	}
}
