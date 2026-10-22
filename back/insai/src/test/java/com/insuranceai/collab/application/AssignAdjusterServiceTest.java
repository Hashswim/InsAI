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
class AssignAdjusterServiceTest {

	@Mock
	CaseRepository cases;

	@Mock
	TimelineEventRepository timeline;

	@InjectMocks
	AssignAdjusterService service;

	private final UUID caseId = UUID.randomUUID();
	private final UUID claimant = UUID.randomUUID();
	private final UUID adjuster = UUID.randomUUID();

	@Test
	@DisplayName("사정사를 배정하고 ADJUSTER_ASSIGNED 이벤트를 기록한다")
	void assigns() {
		Case c = CollabFixtures.openCase(claimant);
		given(cases.findById(caseId)).willReturn(Optional.of(c));
		given(cases.save(any())).willAnswer(inv -> inv.getArgument(0));

		service.assign(caseId, adjuster);

		assertThat(c.getAdjusterId()).isEqualTo(adjuster);
		verify(timeline).save(argThat(ev -> ev.getType() == TimelineEventType.ADJUSTER_ASSIGNED));
	}

	@Test
	@DisplayName("없는 케이스면 NOT_FOUND")
	void caseNotFound() {
		given(cases.findById(caseId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.assign(caseId, adjuster))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("이미 사정사가 배정된 케이스면 CONFLICT")
	void alreadyAssigned() {
		Case c = CollabFixtures.caseAt(CaseStatus.OPEN, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));

		assertThatThrownBy(() -> service.assign(caseId, UUID.randomUUID()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
	}
}
