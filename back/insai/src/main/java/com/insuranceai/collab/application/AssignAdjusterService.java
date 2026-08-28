package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.TimelineEventRepository;
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

	/**
	 * @throws com.insuranceai.common.error.BusinessException NOT_FOUND - 없는 케이스,
	 *         CONFLICT - 이미 사정사가 배정됨
	 */
	@Transactional
	public void assign(UUID caseId, UUID adjusterId) {
		throw new UnsupportedOperationException("TODO: AssignAdjusterService.assign");
	}
}
