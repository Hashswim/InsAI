package com.insuranceai.collab.domain;

import com.insuranceai.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 손해사정사가 청구인에게 특정 서류(양식)를 요청하는 항목.
 * 상태: PENDING → SUBMITTED → ACCEPTED / REJECTED (REJECTED 후 재제출 시 SUBMITTED 로 복귀)
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
		throw new UnsupportedOperationException("TODO: DocumentRequest.create");
	}

	public void markSubmitted() {
		throw new UnsupportedOperationException("TODO: DocumentRequest.markSubmitted");
	}

	public void accept() {
		throw new UnsupportedOperationException("TODO: DocumentRequest.accept");
	}

	/** reason 이 공백이면 BusinessException(INVALID_INPUT) */
	public void reject(String reason) {
		throw new UnsupportedOperationException("TODO: DocumentRequest.reject");
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
