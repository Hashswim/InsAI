package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.Decision;
import com.insuranceai.collab.domain.DecisionRepository;
import com.insuranceai.collab.domain.TimelineEvent;
import com.insuranceai.collab.domain.TimelineEventRepository;
import com.insuranceai.collab.domain.TimelineEventType;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RegisterDecisionService {

	private final CaseRepository cases;
	private final DecisionRepository decisions;
	private final TimelineEventRepository timeline;

	public RegisterDecisionService(CaseRepository cases,
	                               DecisionRepository decisions,
	                               TimelineEventRepository timeline) {
		this.cases = cases;
		this.decisions = decisions;
		this.timeline = timeline;
	}

	/**
	 * 배정된 손해사정사만, 케이스가 UNDER_REVIEW 일 때만 결과를 등록할 수 있다.
	 * 성공 시 케이스 상태를 DECISION_MADE 로 전이한다.
	 *
	 * @return 생성된 Decision id
	 */
	@Transactional
	public UUID register(RegisterDecisionCommand command) {
		Case caseAggregate = cases.findById(command.caseId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "케이스를 찾을 수 없습니다."));

		if (!command.adjusterId().equals(caseAggregate.getAdjusterId())) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "배정된 손해사정사만 결과를 등록할 수 있습니다.");
		}
		caseAggregate.recordDecision();

		Decision decision = Decision.of(caseAggregate.getId(), command.adjusterId(), command.rawText());
		Decision saved = decisions.save(decision);

		cases.save(caseAggregate);
		timeline.save(TimelineEvent.of(
				caseAggregate.getId(), TimelineEventType.DECISION_MADE, command.adjusterId()));

		return saved.getId();
	}
}
