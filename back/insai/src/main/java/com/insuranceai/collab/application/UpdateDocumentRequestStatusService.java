package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.DocumentRequest;
import com.insuranceai.collab.domain.DocumentRequestRepository;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
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
	 */
	@Transactional
	public void updateStatus(UUID documentRequestId, DocumentRequestAction action, String reason) {
		DocumentRequest request = documentRequests.findById(documentRequestId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "서류 요청을 찾을 수 없습니다."));

		switch (action) {
			case ACCEPT -> request.accept();
			case REJECT -> request.reject(reason);
		}
	}
}
