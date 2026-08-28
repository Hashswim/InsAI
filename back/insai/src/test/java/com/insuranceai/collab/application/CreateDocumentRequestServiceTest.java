package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.CaseStatus;
import com.insuranceai.collab.domain.DocumentRequestRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreateDocumentRequestServiceTest {

	@Mock
	CaseRepository cases;

	@Mock
	DocumentRequestRepository documentRequests;

	@Mock
	TimelineEventRepository timeline;

	@InjectMocks
	CreateDocumentRequestService service;

	private final UUID caseId = UUID.randomUUID();
	private final UUID claimant = UUID.randomUUID();
	private final UUID adjuster = UUID.randomUUID();
	private final UUID templateId = UUID.randomUUID();

	private CreateDocumentRequestCommand command(UUID requester) {
		return new CreateDocumentRequestCommand(caseId, requester, templateId, "진단서", null);
	}

	@Test
	@DisplayName("배정된 사정사가 생성하면 케이스가 DOCS_REQUESTED 로 전이되고 id 를 반환한다")
	void createsAndTransitions() {
		Case c = CollabFixtures.caseAt(CaseStatus.OPEN, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));
		given(documentRequests.save(any())).willAnswer(inv -> inv.getArgument(0));
		given(cases.save(any())).willAnswer(inv -> inv.getArgument(0));

		UUID id = service.create(command(adjuster));

		assertThat(id).isNotNull();
		assertThat(c.getStatus()).isEqualTo(CaseStatus.DOCS_REQUESTED);
		verify(timeline).save(argThat(ev -> ev.getType() == TimelineEventType.DOCS_REQUESTED));
	}

	@Test
	@DisplayName("배정된 사정사가 아니면 FORBIDDEN, 저장하지 않는다")
	void notAssignedAdjuster() {
		Case c = CollabFixtures.caseAt(CaseStatus.OPEN, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));

		assertThatThrownBy(() -> service.create(command(claimant)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);

		verify(documentRequests, never()).save(any());
	}

	@Test
	@DisplayName("CLOSED 케이스면 CONFLICT")
	void closedCase() {
		Case c = CollabFixtures.caseAt(CaseStatus.CLOSED, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));

		assertThatThrownBy(() -> service.create(command(adjuster)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
	}

	@Test
	@DisplayName("없는 케이스면 NOT_FOUND")
	void caseNotFound() {
		given(cases.findById(caseId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(command(adjuster)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}
}
