package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.DocumentRequest;
import com.insuranceai.collab.domain.DocumentRequestRepository;
import com.insuranceai.collab.domain.TimelineEvent;
import com.insuranceai.collab.domain.TimelineEventRepository;
import com.insuranceai.collab.domain.TimelineEventType;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreateDocumentRequestService {

	private final CaseRepository cases;
	private final DocumentRequestRepository documentRequests;
	private final TimelineEventRepository timeline;

	public CreateDocumentRequestService(CaseRepository cases,
	                                    DocumentRequestRepository documentRequests,
	                                    TimelineEventRepository timeline) {
		this.cases = cases;
		this.documentRequests = documentRequests;
		this.timeline = timeline;
	}

	/**
	 * 배정된 손해사정사만 생성 가능. 성공 시 케이스 상태를 DOCS_REQUESTED 로 전이하고 타임라인에 기록한다.
	 *
	 * @return 생성된 DocumentRequest id
	 */
	@Transactional
	public UUID create(CreateDocumentRequestCommand command) {
		Case caseAggregate = cases.findById(command.caseId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "케이스를 찾을 수 없습니다."));

		if (!command.requesterId().equals(caseAggregate.getAdjusterId())) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "배정된 손해사정사만 서류를 요청할 수 있습니다.");
		}
		caseAggregate.markDocsRequested();

		DocumentRequest saved = documentRequests.save(DocumentRequest.create(
				caseAggregate.getId(), command.requesterId(), command.templateId(),
				command.title(), command.dueDate()));
		cases.save(caseAggregate);
		timeline.save(TimelineEvent.of(caseAggregate.getId(), TimelineEventType.DOCS_REQUESTED, command.requesterId()));

		return saved.getId();
	}
}
