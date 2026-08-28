package com.insuranceai.collab.domain;

import com.insuranceai.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * 손해사정사의 사정 결과. rawText 는 사정사가 작성한 원문, plainText 는 AI 평문 번역(후처리).
 */
@Entity
@Table(name = "decisions")
public class Decision extends BaseEntity {

	@Column(nullable = false)
	private UUID caseId;

	@Column(nullable = false)
	private UUID adjusterId;

	@Column(nullable = false, length = 8000)
	private String rawText;

	@Column(length = 8000)
	private String plainText;

	protected Decision() {
	}

	/** rawText 가 공백이면 BusinessException(INVALID_INPUT) */
	public static Decision of(UUID caseId, UUID adjusterId, String rawText) {
		Decision decision = new Decision();
		decision.assignId();
		decision.caseId = Objects.requireNonNull(caseId, "caseId");
		decision.adjusterId = Objects.requireNonNull(adjusterId, "adjusterId");
		decision.rawText = requireText(rawText, "사정 결과 원문");
		return decision;
	}

	public void applyPlainText(String plainText) {
		this.plainText = plainText;
	}

	public UUID getCaseId() {
		return caseId;
	}

	public UUID getAdjusterId() {
		return adjusterId;
	}

	public String getRawText() {
		return rawText;
	}

	public String getPlainText() {
		return plainText;
	}
}
