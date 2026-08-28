package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.MessageRepository;
import com.insuranceai.collab.domain.TimelineEventRepository;
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
	 * @throws com.insuranceai.common.error.BusinessException NOT_FOUND - 없는 케이스,
	 *         FORBIDDEN - 비참여자, CONFLICT - CLOSED 케이스
	 */
	@Transactional
	public UUID post(PostMessageCommand command) {
		throw new UnsupportedOperationException("TODO: PostMessageService.post");
	}
}
