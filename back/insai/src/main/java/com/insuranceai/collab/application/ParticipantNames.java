package com.insuranceai.collab.application;

import java.util.UUID;

/**
 * 계정 id → 표시 이름. 구현은 auth 모듈을 참조하는 어댑터(collab.infrastructure).
 */
public interface ParticipantNames {

	/** 없거나 null 이면 null */
	String nameOf(UUID accountId);
}
