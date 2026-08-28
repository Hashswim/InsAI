package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.DocumentRequestRepository;
import com.insuranceai.collab.domain.TimelineEventRepository;
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
	 * @throws com.insuranceai.common.error.BusinessException NOT_FOUND - 없는 케이스,
	 *         FORBIDDEN - 배정 사정사가 아님, CONFLICT - CLOSED 케이스
	 */
	@Transactional
	public UUID create(CreateDocumentRequestCommand command) {
		throw new UnsupportedOperationException("TODO: CreateDocumentRequestService.create");
	}
}
