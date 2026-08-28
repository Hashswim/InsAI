package com.insuranceai.form.application;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.form.domain.FormSubmission;
import com.insuranceai.form.domain.FormSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class SaveDraftService {

	private final FormSubmissionRepository submissions;

	public SaveDraftService(FormSubmissionRepository submissions) {
		this.submissions = submissions;
	}

	/** 부분 저장(임시). 같은 documentRequestId 의 제출물이 있으면 갱신, 없으면 새 DRAFT 생성. */
	@Transactional
	public UUID saveDraft(SaveDraftCommand command) {
		FormSubmission submission = command.documentRequestId() == null ? null
				: submissions.findByDocumentRequestId(command.documentRequestId()).orElse(null);
		if (submission == null) {
			submission = FormSubmission.create(
					command.documentRequestId(), command.caseId(), command.templateId(), command.submittedBy());
		}
		submission.saveDraft(command.data());
		return submissions.save(submission).getId();
	}

	/** 이미 있는 제출물의 임시 데이터를 갱신한다. */
	@Transactional
	public void updateDraft(UUID submissionId, Map<String, Object> data) {
		FormSubmission submission = submissions.findById(submissionId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "제출물을 찾을 수 없습니다."));
		submission.saveDraft(data);
	}
}
