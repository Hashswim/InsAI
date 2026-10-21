package com.insuranceai.collab.domain;

import com.insuranceai.common.domain.BaseEntity;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 손해사정사가 청구인에게 특정 서류(양식)를 요청하는 항목.
 * 상태: PENDING → SUBMITTED → ACCEPTED / REJECTED / SUPPLEMENT_REQUESTED
 * (REJECTED·SUPPLEMENT_REQUESTED 후 재제출 시 SUBMITTED 로 복귀)
 */
@Entity
@Table(name = "document_requests")
public class DocumentRequest extends BaseEntity {

	@Column(nullable = false)
	private UUID caseId;

	@Column(nullable = false)
	private UUID requestedBy;

	@Column(nullable = false)
	private UUID templateId;

	@Column(nullable = false)
	private String title;

	@Column
	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DocumentRequestStatus status;

	@Column
	private String rejectionReason;

	protected DocumentRequest() {
	}

	public static DocumentRequest create(UUID caseId, UUID requestedBy, UUID templateId,
	                                     String title, LocalDate dueDate) {
		DocumentRequest request = new DocumentRequest();
		request.assignId();
		request.caseId = Objects.requireNonNull(caseId, "caseId");
		request.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
		request.templateId = Objects.requireNonNull(templateId, "templateId");
		request.title = requireText(title, "제목");
		request.dueDate = dueDate;
		request.status = DocumentRequestStatus.PENDING;
		return request;
	}

	public void markSubmitted() {
		if (status != DocumentRequestStatus.PENDING && status != DocumentRequestStatus.REJECTED
				&& status != DocumentRequestStatus.SUPPLEMENT_REQUESTED) {
			throw conflict();
		}
		this.status = DocumentRequestStatus.SUBMITTED;
		this.rejectionReason = null;
	}

	public void accept() {
		requireSubmitted();
		this.status = DocumentRequestStatus.ACCEPTED;
	}

	/** reason 이 공백이면 BusinessException(INVALID_INPUT) */
	public void reject(String reason) {
		requireSubmitted();
		this.rejectionReason = requireText(reason, "반려 사유");
		this.status = DocumentRequestStatus.REJECTED;
	}

	/** reason 이 공백이면 BusinessException(INVALID_INPUT) */
	public void requestSupplement(String reason) {
		requireSubmitted();
		this.rejectionReason = requireText(reason, "보완 요청 사유");
		this.status = DocumentRequestStatus.SUPPLEMENT_REQUESTED;
	}

	private void requireSubmitted() {
		if (status != DocumentRequestStatus.SUBMITTED) {
			throw conflict();
		}
	}

	private BusinessException conflict() {
		return new BusinessException(ErrorCode.CONFLICT, "현재 상태(" + status + ")에서 처리할 수 없습니다.");
	}

	public UUID getCaseId() {
		return caseId;
	}

	public UUID getRequestedBy() {
		return requestedBy;
	}

	public UUID getTemplateId() {
		return templateId;
	}

	public String getTitle() {
		return title;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public DocumentRequestStatus getStatus() {
		return status;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}
}
