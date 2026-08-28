package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.TimelineEvent;
import com.insuranceai.collab.domain.TimelineEventRepository;
import com.insuranceai.collab.domain.TimelineEventType;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AssignAdjusterService {

	private final CaseRepository cases;
	private final TimelineEventRepository timeline;

	public AssignAdjusterService(CaseRepository cases, TimelineEventRepository timeline) {
		this.cases = cases;
		this.timeline = timeline;
	}

	@Transactional
	public void assign(UUID caseId, UUID adjusterId) {
		Case caseAggregate = cases.findById(caseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "케이스를 찾을 수 없습니다."));
		caseAggregate.assignAdjuster(adjusterId);
		cases.save(caseAggregate);
		timeline.save(TimelineEvent.of(caseAggregate.getId(), TimelineEventType.ADJUSTER_ASSIGNED, adjusterId));
	}
}
