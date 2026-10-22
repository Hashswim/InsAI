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

/**
 * form 도메인에서 제출이 성공하면 호출된다. 서류 요청을 SUBMITTED 로,
 * 케이스를 DOCS_SUBMITTED 로 전이하고 타임라인에 기록한다.
 */
@Service
public class MarkDocumentRequestSubmittedService {

	private final DocumentRequestRepository documentRequests;
	private final CaseRepository cases;
	private final TimelineEventRepository timeline;

	public MarkDocumentRequestSubmittedService(DocumentRequestRepository documentRequests,
	                                           CaseRepository cases,
	                                           TimelineEventRepository timeline) {
		this.documentRequests = documentRequests;
		this.cases = cases;
		this.timeline = timeline;
	}

	@Transactional
	public void markSubmitted(UUID documentRequestId, UUID actorId) {
		DocumentRequest request = documentRequests.findById(documentRequestId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "서류 요청을 찾을 수 없습니다."));
		request.markSubmitted();
		documentRequests.save(request);

		Case caseAggregate = cases.findById(request.getCaseId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "케이스를 찾을 수 없습니다."));
		caseAggregate.markDocsSubmitted();
		cases.save(caseAggregate);

		timeline.save(TimelineEvent.of(caseAggregate.getId(), TimelineEventType.DOCS_SUBMITTED, actorId));
	}
}
