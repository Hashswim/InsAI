package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.TimelineEvent;
import com.insuranceai.collab.domain.TimelineEventRepository;
import com.insuranceai.collab.domain.TimelineEventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OpenCaseService {

	private final CaseRepository cases;
	private final TimelineEventRepository timeline;

	public OpenCaseService(CaseRepository cases, TimelineEventRepository timeline) {
		this.cases = cases;
		this.timeline = timeline;
	}

	/** @return 생성된 케이스 id. CASE_OPENED 타임라인 이벤트를 함께 기록한다. */
	@Transactional
	public UUID open(OpenCaseCommand command) {
		Case created = cases.save(Case.open(command.claimantId(), command.title()));
		timeline.save(TimelineEvent.of(created.getId(), TimelineEventType.CASE_OPENED, command.claimantId()));
		return created.getId();
	}
}
