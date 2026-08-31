package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.CaseStatus;
import com.insuranceai.collab.domain.Decision;
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
class DecisionQueryAndCloseServiceTest {

	@Mock CaseRepository cases;
	@Mock DecisionRepository decisions;
	@Mock TimelineEventRepository timeline;
	@Mock ParticipantNames names;

	@InjectMocks GetCaseDecisionService getCaseDecisionService;
	@InjectMocks CloseCaseService closeCaseService;

	private final UUID caseId = UUID.randomUUID();
	private final UUID claimant = UUID.randomUUID();
	private final UUID adjuster = UUID.randomUUID();

	@Test
	@DisplayName("getDecision: 있으면 뷰(사정사 이름 포함), 없으면 NOT_FOUND")
	void getDecision() {
		given(decisions.findByCaseId(caseId))
				.willReturn(Optional.of(Decision.of(caseId, adjuster, "지급 승인")));
		given(names.nameOf(adjuster)).willReturn("김사정");

		DecisionView view = getCaseDecisionService.get(caseId);
		assertThat(view.rawText()).isEqualTo("지급 승인");
		assertThat(view.adjusterName()).isEqualTo("김사정");

		given(decisions.findByCaseId(any())).willReturn(Optional.empty());
		assertThatThrownBy(() -> getCaseDecisionService.get(UUID.randomUUID()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("close: 배정 사정사가 DECISION_MADE 에서 종결 -> CLOSED + CASE_CLOSED")
	void close() {
		Case c = CollabFixtures.caseAt(CaseStatus.DECISION_MADE, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));
		given(cases.save(any())).willAnswer(i -> i.getArgument(0));

		closeCaseService.close(caseId, adjuster);

		assertThat(c.getStatus()).isEqualTo(CaseStatus.CLOSED);
		verify(timeline).save(argThat(ev -> ev.getType() == TimelineEventType.CASE_CLOSED));
	}

	@Test
	@DisplayName("close: 배정 사정사가 아니면 FORBIDDEN")
	void closeNotAdjuster() {
		Case c = CollabFixtures.caseAt(CaseStatus.DECISION_MADE, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));

		assertThatThrownBy(() -> closeCaseService.close(caseId, UUID.randomUUID()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("close: DECISION_MADE 가 아니면 CONFLICT")
	void closeWrongState() {
		Case c = CollabFixtures.caseAt(CaseStatus.UNDER_REVIEW, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));

		assertThatThrownBy(() -> closeCaseService.close(caseId, adjuster))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
	}
}
