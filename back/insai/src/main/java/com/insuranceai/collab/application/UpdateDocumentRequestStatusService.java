package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.DocumentRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateDocumentRequestStatusService {

	private final DocumentRequestRepository documentRequests;

	public UpdateDocumentRequestStatusService(DocumentRequestRepository documentRequests) {
		this.documentRequests = documentRequests;
	}

	/**
	 * @param reason REJECT 일 때 필수 (공백이면 INVALID_INPUT)
	 * @throws com.insuranceai.common.error.BusinessException NOT_FOUND - 없는 요청,
	 *         INVALID_INPUT - reject 사유 누락
	 */
	@Transactional
	public void updateStatus(UUID documentRequestId, DocumentRequestAction action, String reason) {
		throw new UnsupportedOperationException("TODO: UpdateDocumentRequestStatusService.updateStatus");
	}
}
