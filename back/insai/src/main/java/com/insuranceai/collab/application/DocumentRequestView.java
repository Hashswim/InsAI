package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.DocumentRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 서류 요청 조회용 읽기 모델.
 */
public record DocumentRequestView(
		UUID id,
		UUID caseId,
		UUID templateId,
		String title,
		String status,
		LocalDate dueDate,
		String rejectionReason,
		Instant createdAt
) {

	public static DocumentRequestView from(DocumentRequest r) {
		return new DocumentRequestView(
				r.getId(), r.getCaseId(), r.getTemplateId(), r.getTitle(),
				r.getStatus().name(), r.getDueDate(), r.getRejectionReason(), r.getCreatedAt());
	}
}
