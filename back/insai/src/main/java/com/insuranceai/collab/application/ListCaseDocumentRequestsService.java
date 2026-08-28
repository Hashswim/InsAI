package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.DocumentRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListCaseDocumentRequestsService {

	private final DocumentRequestRepository documentRequests;

	public ListCaseDocumentRequestsService(DocumentRequestRepository documentRequests) {
		this.documentRequests = documentRequests;
	}

	@Transactional(readOnly = true)
	public List<DocumentRequestView> list(UUID caseId) {
		return documentRequests.findByCaseId(caseId).stream()
				.map(request -> new DocumentRequestView(
						request.getId(),
						request.getCaseId(),
						request.getTemplateId(),
						request.getTitle(),
						request.getStatus().name(),
						request.getDueDate(),
						request.getRejectionReason()))
				.toList();
	}
}
