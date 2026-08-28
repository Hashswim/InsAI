package com.insuranceai.form.infrastructure;

import com.insuranceai.collab.application.MarkDocumentRequestSubmittedService;
import com.insuranceai.form.application.DocumentRequestSubmissionPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * form → collab 경계 어댑터. 제출 성립을 collab 서류 요청/케이스 상태 전이로 위임한다.
 */
@Component
public class CollabDocumentRequestSubmissionAdapter implements DocumentRequestSubmissionPort {

	private final MarkDocumentRequestSubmittedService markDocumentRequestSubmittedService;

	public CollabDocumentRequestSubmissionAdapter(
			MarkDocumentRequestSubmittedService markDocumentRequestSubmittedService) {
		this.markDocumentRequestSubmittedService = markDocumentRequestSubmittedService;
	}

	@Override
	public void onFormSubmitted(UUID documentRequestId, UUID submittedBy) {
		markDocumentRequestSubmittedService.markSubmitted(documentRequestId, submittedBy);
	}
}
