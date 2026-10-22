package com.insuranceai.form.application;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.form.application.AiStructurePort.AiStructureResult;
import com.insuranceai.form.domain.FormSubmission;
import com.insuranceai.form.domain.FormSubmissionRepository;
import com.insuranceai.form.domain.FormTemplate;
import com.insuranceai.form.domain.FormTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SubmitFormService {

	private static final Logger log = LoggerFactory.getLogger(SubmitFormService.class);

	private final FormTemplateRepository templates;
	private final FormSubmissionRepository submissions;
	private final AiStructurePort aiStructurePort;
	private final DocumentRequestSubmissionPort documentRequestSubmissionPort;

	public SubmitFormService(FormTemplateRepository templates,
	                         FormSubmissionRepository submissions,
	                         AiStructurePort aiStructurePort,
	                         DocumentRequestSubmissionPort documentRequestSubmissionPort) {
		this.templates = templates;
		this.submissions = submissions;
		this.aiStructurePort = aiStructurePort;
		this.documentRequestSubmissionPort = documentRequestSubmissionPort;
	}

	/**
	 * @return 제출물 id
	 * @throws BusinessException NOT_FOUND - 없는 템플릿, INVALID_INPUT - 정의되지 않은 필드 key
	 */
	@Transactional
	public UUID submit(SubmitFormCommand command) {
		FormTemplate template = templates.findById(command.templateId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "템플릿을 찾을 수 없습니다."));

		FormSubmission submission = command.documentRequestId() == null ? null
				: submissions.findByDocumentRequestId(command.documentRequestId()).orElse(null);
		if (submission == null) {
			submission = FormSubmission.create(
					command.documentRequestId(), command.caseId(), command.templateId(), command.submittedBy());
		}

		submission.submit(template, command.data());

		try {
			AiStructureResult result = aiStructurePort.structure(command.data(), template);
			submission.applyAiStructured(result.fields(), result.missingFields());
		} catch (RuntimeException e) {
			log.warn("AI 구조화 실패 - 원본 데이터로 제출을 진행합니다. submissionId={}", submission.getId(), e);
		}

		FormSubmission saved = submissions.save(submission);

		if (command.documentRequestId() != null) {
			documentRequestSubmissionPort.onFormSubmitted(command.documentRequestId(), command.submittedBy());
		}

		return saved.getId();
	}
}
