package com.insuranceai.collab.domain;

import com.insuranceai.common.domain.BaseEntity;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Objects;
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

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private ClaimType claimType;

	protected Case() {
	}

	public static Case open(UUID claimantId, String title) {
		return open(claimantId, title, ClaimType.OTHER);
	}

	public static Case open(UUID claimantId, String title, ClaimType claimType) {
		Case c = new Case();
		c.assignId();
		c.claimantId = Objects.requireNonNull(claimantId, "claimantId");
		c.title = requireText(title, "제목");
		c.claimType = claimType == null ? ClaimType.OTHER : claimType;
		c.status = CaseStatus.OPEN;
		return c;
	}

	public void assignAdjuster(UUID adjusterId) {
		if (this.adjusterId != null) {
			throw new BusinessException(ErrorCode.CONFLICT, "이미 손해사정사가 배정되었습니다.");
		}
		this.adjusterId = Objects.requireNonNull(adjusterId, "adjusterId");
	}

	public void markDocsRequested() {
		if (status != CaseStatus.OPEN && status != CaseStatus.DOCS_SUBMITTED && status != CaseStatus.UNDER_REVIEW) {
			throw conflict("서류를 요청");
		}
		this.status = CaseStatus.DOCS_REQUESTED;
	}

	public void markDocsSubmitted() {
		requireStatus(CaseStatus.DOCS_REQUESTED, "서류 제출을 반영");
		this.status = CaseStatus.DOCS_SUBMITTED;
	}

	public void startReview() {
		requireStatus(CaseStatus.DOCS_SUBMITTED, "검토를 시작");
		this.status = CaseStatus.UNDER_REVIEW;
	}

	public void recordDecision() {
		requireStatus(CaseStatus.UNDER_REVIEW, "사정 결과를 등록");
		this.status = CaseStatus.DECISION_MADE;
	}

	public void close() {
		requireStatus(CaseStatus.DECISION_MADE, "케이스를 종료");
		this.status = CaseStatus.CLOSED;
	}

	/** userId 가 청구인도 배정 사정사도 아니면 BusinessException(FORBIDDEN) */
	public void requireParticipant(UUID userId) {
		if (userId != null && (userId.equals(claimantId) || userId.equals(adjusterId))) {
			return;
		}
		throw new BusinessException(ErrorCode.FORBIDDEN, "이 케이스의 참여자가 아닙니다.");
	}

	private void requireStatus(CaseStatus expected, String action) {
		if (this.status != expected) {
			throw conflict(action);
		}
	}

	private BusinessException conflict(String action) {
		return new BusinessException(ErrorCode.CONFLICT,
				"현재 상태(" + status + ")에서는 " + action + "할 수 없습니다.");
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

	public ClaimType getClaimType() {
		return claimType;
	}
}
