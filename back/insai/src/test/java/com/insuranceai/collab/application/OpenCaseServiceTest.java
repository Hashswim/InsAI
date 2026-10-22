package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.CaseStatus;
import com.insuranceai.collab.domain.TimelineEvent;
import com.insuranceai.collab.domain.TimelineEventRepository;
import com.insuranceai.collab.domain.TimelineEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OpenCaseServiceTest {

	@Mock
	CaseRepository cases;

	@Mock
	TimelineEventRepository timeline;

	@InjectMocks
	OpenCaseService service;

	@Test
	@DisplayName("케이스를 OPEN 으로 저장하고 id 를 반환하며 CASE_OPENED 이벤트를 기록한다")
	void opensCase() {
		given(cases.save(any(Case.class))).willAnswer(inv -> inv.getArgument(0));

		UUID id = service.open(new OpenCaseCommand(
				UUID.randomUUID(), "자동차 사고 청구", com.insuranceai.collab.domain.ClaimType.DEATH));

		assertThat(id).isNotNull();

		ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
		verify(cases).save(caseCaptor.capture());
		assertThat(caseCaptor.getValue().getStatus()).isEqualTo(CaseStatus.OPEN);

		ArgumentCaptor<TimelineEvent> eventCaptor = ArgumentCaptor.forClass(TimelineEvent.class);
		verify(timeline).save(eventCaptor.capture());
		assertThat(eventCaptor.getValue().getType()).isEqualTo(TimelineEventType.CASE_OPENED);
	}
}
