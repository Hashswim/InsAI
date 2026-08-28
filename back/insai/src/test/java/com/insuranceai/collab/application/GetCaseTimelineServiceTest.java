package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.TimelineEvent;
import com.insuranceai.collab.domain.TimelineEventRepository;
import com.insuranceai.collab.domain.TimelineEventType;
import com.insuranceai.support.fixture.CollabFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetCaseTimelineServiceTest {

	@Mock
	TimelineEventRepository timeline;

	@InjectMocks
	GetCaseTimelineService service;

	@Test
	@DisplayName("이벤트를 시간 오름차순 그대로 TimelineEntry 로 매핑해 반환한다")
	void returnsChronological() {
		UUID caseId = UUID.randomUUID();
		TimelineEvent opened = CollabFixtures.timelineEvent(caseId, TimelineEventType.CASE_OPENED);
		TimelineEvent assigned = CollabFixtures.timelineEvent(caseId, TimelineEventType.ADJUSTER_ASSIGNED);
		given(timeline.findByCaseIdOrderByCreatedAtAsc(caseId)).willReturn(List.of(opened, assigned));

		List<TimelineEntry> entries = service.timeline(caseId);

		assertThat(entries).extracting(TimelineEntry::type)
				.containsExactly(TimelineEventType.CASE_OPENED, TimelineEventType.ADJUSTER_ASSIGNED);
	}
}
