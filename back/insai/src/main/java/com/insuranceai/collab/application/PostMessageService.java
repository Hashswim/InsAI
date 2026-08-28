package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.Message;
import com.insuranceai.collab.domain.MessageRepository;
import com.insuranceai.collab.domain.TimelineEvent;
import com.insuranceai.collab.domain.TimelineEventRepository;
import com.insuranceai.collab.domain.TimelineEventType;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PostMessageService {

	private final CaseRepository cases;
	private final MessageRepository messages;
	private final TimelineEventRepository timeline;

	public PostMessageService(CaseRepository cases,
	                          MessageRepository messages,
	                          TimelineEventRepository timeline) {
		this.cases = cases;
		this.messages = messages;
		this.timeline = timeline;
	}

	/**
	 * 케이스 참여자만 메시지를 작성할 수 있다.
	 *
	 * @return 생성된 메시지 id
	 */
	@Transactional
	public UUID post(PostMessageCommand command) {
		Case caseAggregate = cases.findById(command.caseId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "케이스를 찾을 수 없습니다."));

		caseAggregate.requireParticipant(command.senderId());

		Message saved = messages.save(Message.of(
				caseAggregate.getId(), command.senderId(), command.senderRole(), command.body()));
		timeline.save(TimelineEvent.of(
				caseAggregate.getId(), TimelineEventType.MESSAGE_POSTED, command.senderId()));

		return saved.getId();
	}
}
