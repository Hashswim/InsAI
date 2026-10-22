package com.insuranceai.form.application;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.form.domain.FormSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetSubmissionService {

	private final FormSubmissionRepository submissions;

	public GetSubmissionService(FormSubmissionRepository submissions) {
		this.submissions = submissions;
	}

	/** @throws BusinessException NOT_FOUND - 없는 제출물 */
	@Transactional(readOnly = true)
	public FormSubmissionView get(UUID submissionId) {
		return submissions.findById(submissionId)
				.map(FormSubmissionView::from)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "제출물을 찾을 수 없습니다."));
	}

	/** 서류 요청에 대한 제출물. @throws BusinessException NOT_FOUND - 아직 제출 안 됨 */
	@Transactional(readOnly = true)
	public FormSubmissionView getByDocumentRequestId(UUID documentRequestId) {
		return submissions.findByDocumentRequestId(documentRequestId)
				.map(FormSubmissionView::from)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "아직 제출된 내용이 없습니다."));
	}
}
