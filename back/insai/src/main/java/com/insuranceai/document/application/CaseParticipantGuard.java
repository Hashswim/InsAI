package com.insuranceai.document.application;

import java.util.UUID;

/**
 * 케이스 참여자 확인 포트 → collab. 비참여자면 BusinessException(FORBIDDEN).
 */
public interface CaseParticipantGuard {

	void requireParticipant(UUID caseId, UUID userId);
}
