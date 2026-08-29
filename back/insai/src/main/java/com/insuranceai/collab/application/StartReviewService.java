package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.TimelineEvent;
import com.insuranceai.collab.domain.TimelineEventRepository;
import com.insuranceai.collab.domain.TimelineEventType;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 사정사가 제출물 검토를 시작한다: DOCS_SUBMITTED → UNDER_REVIEW.
 */
@Service
public class StartReviewService {

	private final CaseRepository cases;
	private final TimelineEventRepository timeline;

	public StartReviewService(CaseRepository cases, TimelineEventRepository timeline) {
		this.cases = cases;
		this.timeline = timeline;
	}

	/**
	 * @throws BusinessException NOT_FOUND - 없는 케이스, FORBIDDEN - 배정 사정사 아님,
	 *                           CONFLICT - DOCS_SUBMITTED 아님
	 */
	@Transactional
	public void startReview(UUID caseId, UUID adjusterId) {
		Case caseAggregate = cases.findById(caseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "케이스를 찾을 수 없습니다."));
		if (!adjusterId.equals(caseAggregate.getAdjusterId())) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "배정된 손해사정사만 검토를 시작할 수 있습니다.");
		}
		caseAggregate.startReview();
		cases.save(caseAggregate);
		timeline.save(TimelineEvent.of(caseAggregate.getId(), TimelineEventType.REVIEW_STARTED, adjusterId));
	}
}
