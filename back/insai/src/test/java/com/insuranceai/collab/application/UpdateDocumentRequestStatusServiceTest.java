package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.CaseStatus;
import com.insuranceai.collab.domain.DocumentRequest;
import com.insuranceai.collab.domain.DocumentRequestRepository;
import com.insuranceai.collab.domain.DocumentRequestStatus;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.support.fixture.CollabFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UpdateDocumentRequestStatusServiceTest {

	@Mock
	DocumentRequestRepository documentRequests;

	@Mock
	CaseRepository cases;

	@InjectMocks
	UpdateDocumentRequestStatusService service;

	private final UUID documentRequestId = UUID.randomUUID();

	@Test
	@DisplayName("ACCEPT -> ACCEPTED")
	void accept() {
		DocumentRequest request = CollabFixtures.submittedDocRequest();
		given(documentRequests.findById(documentRequestId)).willReturn(Optional.of(request));

		service.updateStatus(documentRequestId, DocumentRequestAction.ACCEPT, null);

		assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.ACCEPTED);
	}

	@Test
	@DisplayName("REJECT + 사유 -> REJECTED")
	void reject() {
		DocumentRequest request = CollabFixtures.submittedDocRequest();
		given(documentRequests.findById(documentRequestId)).willReturn(Optional.of(request));

		Case claim = submittedCase(request);

		service.updateStatus(documentRequestId, DocumentRequestAction.REJECT, "판독 불가");

		assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.REJECTED);
		assertThat(request.getRejectionReason()).isEqualTo("판독 불가");
		assertThat(claim.getStatus()).isEqualTo(CaseStatus.DOCS_REQUESTED);
	}

	@Test
	@DisplayName("SUPPLEMENT + 사유 -> SUPPLEMENT_REQUESTED, 케이스는 재제출 가능한 DOCS_REQUESTED 로")
	void supplement() {
		DocumentRequest request = CollabFixtures.submittedDocRequest();
		given(documentRequests.findById(documentRequestId)).willReturn(Optional.of(request));
		Case claim = submittedCase(request);

		service.updateStatus(documentRequestId, DocumentRequestAction.SUPPLEMENT, "질병분류기호 기재 필요");

		assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.SUPPLEMENT_REQUESTED);
		assertThat(claim.getStatus()).isEqualTo(CaseStatus.DOCS_REQUESTED);
		claim.markDocsSubmitted();
	}

	private Case submittedCase(DocumentRequest request) {
		Case claim = CollabFixtures.caseAt(CaseStatus.DOCS_SUBMITTED, UUID.randomUUID(), UUID.randomUUID());
		given(cases.findById(request.getCaseId())).willReturn(Optional.of(claim));
		return claim;
	}

	@Test
	@DisplayName("REJECT 인데 사유가 없으면 INVALID_INPUT")
	void rejectWithoutReason() {
		DocumentRequest request = CollabFixtures.submittedDocRequest();
		given(documentRequests.findById(documentRequestId)).willReturn(Optional.of(request));

		assertThatThrownBy(() -> service.updateStatus(documentRequestId, DocumentRequestAction.REJECT, "  "))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("없는 요청이면 NOT_FOUND")
	void notFound() {
		given(documentRequests.findById(documentRequestId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.updateStatus(documentRequestId, DocumentRequestAction.ACCEPT, null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}
}
