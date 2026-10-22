package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.CaseStatus;
import com.insuranceai.collab.domain.DecisionRepository;
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
class RegisterDecisionServiceTest {

	@Mock
	CaseRepository cases;

	@Mock
	DecisionRepository decisions;

	@Mock
	TimelineEventRepository timeline;

	@InjectMocks
	RegisterDecisionService service;

	private final UUID caseId = UUID.randomUUID();
	private final UUID claimant = UUID.randomUUID();
	private final UUID adjuster = UUID.randomUUID();

	@Test
	@DisplayName("배정 사정사가 UNDER_REVIEW 에서 등록하면 케이스가 DECISION_MADE 로 전이된다")
	void registers() {
		Case c = CollabFixtures.caseAt(CaseStatus.UNDER_REVIEW, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));
		given(decisions.save(any())).willAnswer(inv -> inv.getArgument(0));
		given(cases.save(any())).willAnswer(inv -> inv.getArgument(0));

		UUID id = service.register(new RegisterDecisionCommand(caseId, adjuster, "지급 승인. 총 320만원."));

		assertThat(id).isNotNull();
		assertThat(c.getStatus()).isEqualTo(CaseStatus.DECISION_MADE);
		verify(timeline).save(argThat(ev -> ev.getType() == TimelineEventType.DECISION_MADE));
	}

	@Test
	@DisplayName("배정 사정사가 아니면 FORBIDDEN")
	void notAssignedAdjuster() {
		Case c = CollabFixtures.caseAt(CaseStatus.UNDER_REVIEW, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));

		assertThatThrownBy(() -> service.register(
				new RegisterDecisionCommand(caseId, UUID.randomUUID(), "지급 승인")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("UNDER_REVIEW 가 아니면 CONFLICT")
	void wrongState() {
		Case c = CollabFixtures.caseAt(CaseStatus.DOCS_SUBMITTED, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));

		assertThatThrownBy(() -> service.register(
				new RegisterDecisionCommand(caseId, adjuster, "지급 승인")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
	}
}
