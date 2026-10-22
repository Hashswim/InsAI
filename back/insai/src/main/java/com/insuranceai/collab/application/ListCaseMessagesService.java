package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListCaseMessagesService {

	private final MessageRepository messages;

	public ListCaseMessagesService(MessageRepository messages) {
		this.messages = messages;
	}

	@Transactional(readOnly = true)
	public List<MessageView> list(UUID caseId) {
		return messages.findByCaseIdOrderByCreatedAtAsc(caseId).stream()
				.map(MessageView::from)
				.toList();
	}
}
