package com.insuranceai.collab.application;

import com.insuranceai.auth.domain.Role;
import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.CaseStatus;
import com.insuranceai.collab.domain.MessageRepository;
import com.insuranceai.collab.domain.TimelineEventRepository;
import com.insuranceai.collab.domain.TimelineEventType;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.support.fixture.CollabFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostMessageServiceTest {

	@Mock
	CaseRepository cases;

	@Mock
	MessageRepository messages;

	@Mock
	TimelineEventRepository timeline;

	@InjectMocks
	PostMessageService service;

	private final UUID caseId = UUID.randomUUID();
	private final UUID claimant = UUID.randomUUID();
	private final UUID adjuster = UUID.randomUUID();

	@Test
	@DisplayName("참여자가 메시지를 작성하면 저장하고 MESSAGE_POSTED 를 기록한다")
	void postsMessage() {
		Case c = CollabFixtures.caseAt(CaseStatus.DOCS_SUBMITTED, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));
		given(messages.save(any())).willAnswer(inv -> inv.getArgument(0));

		UUID id = service.post(new PostMessageCommand(caseId, claimant, Role.CLAIMANT, "서류 올렸습니다"));

		assertThat(id).isNotNull();
		verify(timeline).save(argThat(ev -> ev.getType() == TimelineEventType.MESSAGE_POSTED));
	}

	@Test
	@DisplayName("비참여자면 FORBIDDEN, 저장하지 않는다")
	void outsiderForbidden() {
		Case c = CollabFixtures.caseAt(CaseStatus.OPEN, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));

		assertThatThrownBy(() -> service.post(
				new PostMessageCommand(caseId, UUID.randomUUID(), Role.CLAIMANT, "안녕하세요")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);

		verify(messages, never()).save(any());
	}

	@Test
	@DisplayName("없는 케이스면 NOT_FOUND")
	void caseNotFound() {
		given(cases.findById(caseId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.post(
				new PostMessageCommand(caseId, claimant, Role.CLAIMANT, "안녕하세요")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}
}
