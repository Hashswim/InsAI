package com.insuranceai.support.fixture;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseStatus;
import com.insuranceai.collab.domain.DocumentRequest;
import com.insuranceai.collab.domain.TimelineEvent;
import com.insuranceai.collab.domain.TimelineEventType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

/**
 * collab 도메인 테스트 픽스처. 상태/식별자는 리플렉션으로 강제 세팅한다(단위 테스트 전용).
 */
public final class CollabFixtures {

	private CollabFixtures() {
	}

	public static Case openCase(UUID claimantId) {
		return Case.open(claimantId, "자동차 사고 청구");
	}

	public static Case caseAt(CaseStatus status, UUID claimantId, UUID adjusterId) {
		Case c = Case.open(claimantId, "자동차 사고 청구");
		ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(c, "adjusterId", adjusterId);
		ReflectionTestUtils.setField(c, "status", status);
		return c;
	}

	public static DocumentRequest submittedDocRequest() {
		DocumentRequest request = DocumentRequest.create(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "진단서", null);
		request.markSubmitted();
		return request;
	}

	public static TimelineEvent timelineEvent(UUID caseId, TimelineEventType type) {
		return TimelineEvent.of(caseId, type, null);
	}
}
