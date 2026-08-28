package com.insuranceai.collab.application;

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

		service.updateStatus(documentRequestId, DocumentRequestAction.REJECT, "판독 불가");

		assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.REJECTED);
		assertThat(request.getRejectionReason()).isEqualTo("판독 불가");
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
