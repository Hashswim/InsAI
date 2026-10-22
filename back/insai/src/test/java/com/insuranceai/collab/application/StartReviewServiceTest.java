package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.CaseStatus;
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
class StartReviewServiceTest {

	@Mock CaseRepository cases;
	@Mock TimelineEventRepository timeline;

	@InjectMocks
	StartReviewService service;

	private final UUID caseId = UUID.randomUUID();
	private final UUID claimant = UUID.randomUUID();
	private final UUID adjuster = UUID.randomUUID();

	@Test
	@DisplayName("배정 사정사가 DOCS_SUBMITTED 에서 시작하면 UNDER_REVIEW + REVIEW_STARTED")
	void startsReview() {
		Case c = CollabFixtures.caseAt(CaseStatus.DOCS_SUBMITTED, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));
		given(cases.save(any())).willAnswer(i -> i.getArgument(0));

		service.startReview(caseId, adjuster);

		assertThat(c.getStatus()).isEqualTo(CaseStatus.UNDER_REVIEW);
		verify(timeline).save(argThat(ev -> ev.getType() == TimelineEventType.REVIEW_STARTED));
	}

	@Test
	@DisplayName("배정 사정사가 아니면 FORBIDDEN")
	void notAssigned() {
		Case c = CollabFixtures.caseAt(CaseStatus.DOCS_SUBMITTED, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));

		assertThatThrownBy(() -> service.startReview(caseId, UUID.randomUUID()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("DOCS_SUBMITTED 가 아니면 CONFLICT")
	void wrongState() {
		Case c = CollabFixtures.caseAt(CaseStatus.DOCS_REQUESTED, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));

		assertThatThrownBy(() -> service.startReview(caseId, adjuster))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
	}
}
