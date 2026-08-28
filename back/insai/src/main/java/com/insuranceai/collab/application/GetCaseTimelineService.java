package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.TimelineEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GetCaseTimelineService {

	private final TimelineEventRepository timeline;

	public GetCaseTimelineService(TimelineEventRepository timeline) {
		this.timeline = timeline;
	}

	/** 케이스의 활동 이벤트를 시간 오름차순으로 반환한다. */
	@Transactional(readOnly = true)
	public List<TimelineEntry> timeline(UUID caseId) {
		return timeline.findByCaseIdOrderByCreatedAtAsc(caseId).stream()
				.map(event -> new TimelineEntry(event.getType(), event.getActorId(), event.getCreatedAt()))
				.toList();
	}
}
