package com.insuranceai.document.infrastructure;

import com.insuranceai.collab.application.CaseAccessService;
import com.insuranceai.document.application.CaseParticipantGuard;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * document → collab 경계 어댑터. 참여자 확인을 collab 케이스 접근 정책에 위임한다.
 */
@Component
public class CollabCaseParticipantGuard implements CaseParticipantGuard {

	private final CaseAccessService caseAccessService;

	public CollabCaseParticipantGuard(CaseAccessService caseAccessService) {
		this.caseAccessService = caseAccessService;
	}

	@Override
	public void requireParticipant(UUID caseId, UUID userId) {
		caseAccessService.requireParticipant(caseId, userId);
	}
}
