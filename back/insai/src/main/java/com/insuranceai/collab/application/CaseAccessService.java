package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CaseAccessService {

	private final CaseRepository cases;

	public CaseAccessService(CaseRepository cases) {
		this.cases = cases;
	}

	/**
	 * @throws BusinessException NOT_FOUND - 없는 케이스, FORBIDDEN - 청구인/배정 사정사가 아님
	 */
	@Transactional(readOnly = true)
	public void requireParticipant(UUID caseId, UUID userId) {
		cases.findById(caseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "케이스를 찾을 수 없습니다."))
				.requireParticipant(userId);
	}
}
