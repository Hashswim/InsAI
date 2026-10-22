package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.CaseStatus;
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
	private final CaseRepository cases;

	public UpdateDocumentRequestStatusService(DocumentRequestRepository documentRequests, CaseRepository cases) {
		this.documentRequests = documentRequests;
		this.cases = cases;
	}

	/**
	 * @param reason REJECT·SUPPLEMENT 일 때 필수 (공백이면 INVALID_INPUT)
	 */
	@Transactional
	public void updateStatus(UUID documentRequestId, DocumentRequestAction action, String reason) {
		DocumentRequest request = documentRequests.findById(documentRequestId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "서류 요청을 찾을 수 없습니다."));

		switch (action) {
			case ACCEPT -> request.accept();
			case REJECT -> request.reject(reason);
			case SUPPLEMENT -> request.requestSupplement(reason);
		}
		if (action != DocumentRequestAction.ACCEPT) reopenForResubmission(request.getCaseId());
	}

	/** 반려·보완 요청된 서류를 청구인이 수정해 다시 제출할 수 있도록 케이스를 서류 요청 단계로 되돌린다. */
	private void reopenForResubmission(UUID caseId) {
		Case caseAggregate = cases.findById(caseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "케이스를 찾을 수 없습니다."));
		if (caseAggregate.getStatus() != CaseStatus.DOCS_REQUESTED) {
			caseAggregate.markDocsRequested();
			cases.save(caseAggregate);
		}
	}
}
