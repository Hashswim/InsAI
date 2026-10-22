package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.CaseStatus;
import com.insuranceai.collab.domain.DocumentRequest;
import com.insuranceai.collab.domain.DocumentRequestRepository;
import com.insuranceai.collab.domain.DocumentRequestStatus;
import com.insuranceai.collab.domain.TimelineEventRepository;
import com.insuranceai.collab.domain.TimelineEventType;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarkDocumentRequestSubmittedServiceTest {

	@Mock DocumentRequestRepository documentRequests;
	@Mock CaseRepository cases;
	@Mock TimelineEventRepository timeline;

	@InjectMocks
	MarkDocumentRequestSubmittedService service;

	private final UUID docReqId = UUID.randomUUID();
	private final UUID caseId = UUID.randomUUID();
	private final UUID claimant = UUID.randomUUID();
	private final UUID adjuster = UUID.randomUUID();

	@Test
	@DisplayName("서류요청 SUBMITTED + 케이스 DOCS_SUBMITTED + 타임라인 기록")
	void marksSubmitted() {
		DocumentRequest request = DocumentRequest.create(caseId, adjuster, UUID.randomUUID(), "진단서", null);
		Case c = CollabFixtures.caseAt(CaseStatus.DOCS_REQUESTED, claimant, adjuster);
		given(documentRequests.findById(docReqId)).willReturn(Optional.of(request));
		given(cases.findById(request.getCaseId())).willReturn(Optional.of(c));
		given(documentRequests.save(any())).willAnswer(i -> i.getArgument(0));
		given(cases.save(any())).willAnswer(i -> i.getArgument(0));

		service.markSubmitted(docReqId, claimant);

		assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.SUBMITTED);
		assertThat(c.getStatus()).isEqualTo(CaseStatus.DOCS_SUBMITTED);
		verify(timeline).save(argThat(ev -> ev.getType() == TimelineEventType.DOCS_SUBMITTED));
	}

	@Test
	@DisplayName("없는 서류요청이면 NOT_FOUND")
	void requestNotFound() {
		given(documentRequests.findById(docReqId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.markSubmitted(docReqId, claimant))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}
}
