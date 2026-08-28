package com.insuranceai.collab;

import com.insuranceai.auth.domain.Role;
import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.CaseStatus;
import com.insuranceai.collab.domain.DocumentRequest;
import com.insuranceai.collab.domain.DocumentRequestRepository;
import com.insuranceai.collab.domain.DocumentRequestStatus;
import com.insuranceai.collab.domain.Message;
import com.insuranceai.collab.domain.MessageRepository;
import com.insuranceai.collab.domain.TimelineEvent;
import com.insuranceai.collab.domain.TimelineEventRepository;
import com.insuranceai.collab.domain.TimelineEventType;
import com.insuranceai.support.AbstractJpaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CollabRepositoryJpaTest extends AbstractJpaTest {

	@Autowired
	CaseRepository cases;

	@Autowired
	DocumentRequestRepository documentRequests;

	@Autowired
	MessageRepository messages;

	@Autowired
	TimelineEventRepository timelineEvents;

	private final UUID claimant = UUID.randomUUID();
	private final UUID adjuster = UUID.randomUUID();

	@Test
	@DisplayName("CaseRepository: findByClaimantId / findByAdjusterId / findByStatus")
	void caseQueries() {
		Case c = Case.open(claimant, "자동차 사고");
		c.assignAdjuster(adjuster);
		cases.save(c);

		assertThat(cases.findByClaimantId(claimant)).hasSize(1);
		assertThat(cases.findByAdjusterId(adjuster)).hasSize(1);
		assertThat(cases.findByStatus(CaseStatus.OPEN)).hasSize(1);
		assertThat(cases.findByStatus(CaseStatus.CLOSED)).isEmpty();
	}

	@Test
	@DisplayName("DocumentRequestRepository: findByCaseId / findByCaseIdAndStatus")
	void documentRequestQueries() {
		UUID caseId = UUID.randomUUID();
		UUID templateId = UUID.randomUUID();
		DocumentRequest pending = DocumentRequest.create(caseId, adjuster, templateId, "진단서", null);
		DocumentRequest submitted = DocumentRequest.create(caseId, adjuster, templateId, "영수증", null);
		submitted.markSubmitted();
		documentRequests.saveAll(List.of(pending, submitted));

		assertThat(documentRequests.findByCaseId(caseId)).hasSize(2);
		assertThat(documentRequests.findByCaseIdAndStatus(caseId, DocumentRequestStatus.PENDING)).hasSize(1);
		assertThat(documentRequests.findByCaseIdAndStatus(caseId, DocumentRequestStatus.SUBMITTED)).hasSize(1);
	}

	@Test
	@DisplayName("MessageRepository: findByCaseIdOrderByCreatedAtAsc")
	void messageOrdering() throws InterruptedException {
		UUID caseId = UUID.randomUUID();
		messages.saveAndFlush(Message.of(caseId, claimant, Role.CLAIMANT, "첫 메시지"));
		Thread.sleep(5);
		messages.saveAndFlush(Message.of(caseId, adjuster, Role.ADJUSTER, "두 번째 메시지"));

		assertThat(messages.findByCaseIdOrderByCreatedAtAsc(caseId))
				.extracting(Message::getBody)
				.containsExactly("첫 메시지", "두 번째 메시지");
	}

	@Test
	@DisplayName("TimelineEventRepository: findByCaseIdOrderByCreatedAtAsc")
	void timelineOrdering() throws InterruptedException {
		UUID caseId = UUID.randomUUID();
		timelineEvents.saveAndFlush(TimelineEvent.of(caseId, TimelineEventType.CASE_OPENED, claimant));
		Thread.sleep(5);
		timelineEvents.saveAndFlush(TimelineEvent.of(caseId, TimelineEventType.ADJUSTER_ASSIGNED, adjuster));

		assertThat(timelineEvents.findByCaseIdOrderByCreatedAtAsc(caseId))
				.extracting(TimelineEvent::getType)
				.containsExactly(TimelineEventType.CASE_OPENED, TimelineEventType.ADJUSTER_ASSIGNED);
	}
}
