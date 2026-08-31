package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetCaseService {

	private final CaseRepository cases;
	private final ParticipantNames names;

	public GetCaseService(CaseRepository cases, ParticipantNames names) {
		this.cases = cases;
		this.names = names;
	}

	/** @throws BusinessException NOT_FOUND - 없는 케이스 */
	@Transactional(readOnly = true)
	public CaseView get(UUID caseId) {
		return cases.findById(caseId)
				.map(c -> CaseView.from(c, names.nameOf(c.getClaimantId()), names.nameOf(c.getAdjusterId())))
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "케이스를 찾을 수 없습니다."));
	}
}
