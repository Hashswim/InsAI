package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.DecisionRepository;
import com.insuranceai.collab.domain.TimelineEventRepository;
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
	 * @throws com.insuranceai.common.error.BusinessException NOT_FOUND - 없는 케이스,
	 *         FORBIDDEN - 배정 사정사가 아님, CONFLICT - UNDER_REVIEW 아님
	 */
	@Transactional
	public UUID register(RegisterDecisionCommand command) {
		throw new UnsupportedOperationException("TODO: RegisterDecisionService.register");
	}
}
