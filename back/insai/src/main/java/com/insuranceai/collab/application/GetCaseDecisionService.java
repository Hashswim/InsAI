package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.DecisionRepository;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetCaseDecisionService {

	private final DecisionRepository decisions;
	private final ParticipantNames names;

	public GetCaseDecisionService(DecisionRepository decisions, ParticipantNames names) {
		this.decisions = decisions;
		this.names = names;
	}

	/** @throws BusinessException NOT_FOUND - 등록된 사정 결과 없음 */
	@Transactional(readOnly = true)
	public DecisionView get(UUID caseId) {
		return decisions.findByCaseId(caseId)
				.map(d -> DecisionView.from(d, names.nameOf(d.getAdjusterId())))
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "등록된 사정 결과가 없습니다."));
	}
}
