package com.insuranceai.collab.application;

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
		String rejectionReason
) {
}
