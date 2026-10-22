package com.insuranceai.form.domain;

import com.insuranceai.common.domain.BaseEntity;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.common.persistence.JsonMapConverter;
import com.insuranceai.common.persistence.JsonStringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 청구인이 채운 양식 값. 제출 시 템플릿으로 검증하고 누락 필드를 계산한다.
 * complete=false 여도 제출은 성립하며(사정사가 추가 요청), version 은 재제출마다 올라간다.
 */
@Entity
@Table(name = "form_submissions")
public class FormSubmission extends BaseEntity {

	@Column
	private UUID documentRequestId;

	@Column(nullable = false)
	private UUID caseId;

	@Column(nullable = false)
	private UUID templateId;

	@Column(nullable = false)
	private UUID submittedBy;

	@Convert(converter = JsonMapConverter.class)
	@Column(length = 8000)
	private Map<String, Object> data = new LinkedHashMap<>();

	@Convert(converter = JsonMapConverter.class)
	@Column(length = 8000)
	private Map<String, Object> aiStructured;

	@Convert(converter = JsonStringListConverter.class)
	@Column(length = 2000)
	private List<String> missingFields = new ArrayList<>();

	@Convert(converter = JsonStringListConverter.class)
	@Column(length = 2000)
	private List<String> validationErrors = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SubmissionStatus status;

	@Column(nullable = false)
	private int version;

	@Column(nullable = false)
	private boolean complete;

	protected FormSubmission() {
	}

	public static FormSubmission create(UUID documentRequestId, UUID caseId, UUID templateId, UUID submittedBy) {
		FormSubmission submission = new FormSubmission();
		submission.assignId();
		submission.documentRequestId = documentRequestId;
		submission.caseId = Objects.requireNonNull(caseId, "caseId");
		submission.templateId = Objects.requireNonNull(templateId, "templateId");
		submission.submittedBy = Objects.requireNonNull(submittedBy, "submittedBy");
		submission.status = SubmissionStatus.DRAFT;
		submission.version = 0;
		submission.complete = false;
		return submission;
	}

	/** 부분 저장. 상태는 DRAFT 유지. */
	public void saveDraft(Map<String, Object> partialData) {
		this.data = new LinkedHashMap<>(partialData == null ? Map.of() : partialData);
		this.status = SubmissionStatus.DRAFT;
	}

	/**
	 * 템플릿으로 검증하고 제출한다.
	 *
	 * @throws BusinessException INVALID_INPUT - 템플릿에 정의되지 않은 필드 key 가 포함됨
	 */
	public void submit(FormTemplate template, Map<String, Object> submitted) {
		Map<String, Object> incoming = submitted == null ? Map.of() : submitted;
		for (String key : incoming.keySet()) {
			if (!template.fieldKeys().contains(key)) {
				throw new BusinessException(ErrorCode.INVALID_INPUT, "정의되지 않은 필드입니다: " + key);
			}
		}

		List<String> missing = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (FormField field : template.getFields()) {
			Object value = incoming.get(field.key());
			boolean blank = value == null || (value instanceof String s && s.isBlank());
			if (blank) {
				if (field.required()) {
					missing.add(field.key());
				}
				continue;
			}
			String reason = field.validate(value);
			if (reason != null) {
				errors.add(field.key() + ": " + reason);
			}
		}

		this.data = new LinkedHashMap<>(incoming);
		this.missingFields = missing;
		this.validationErrors = errors;
		this.complete = missing.isEmpty() && errors.isEmpty();
		this.status = SubmissionStatus.SUBMITTED;
		this.version = this.version + 1;
	}

	/** AI 구조화 결과 병합. 사용자가 채운 값이 우선하며, AI 가 채운 누락 필드는 missingFields 에서 제거한다. */
	public void applyAiStructured(Map<String, Object> aiFields, List<String> aiMissing) {
		this.aiStructured = aiFields == null ? null : new LinkedHashMap<>(aiFields);
		if (aiFields != null && !missingFields.isEmpty()) {
			List<String> remaining = new ArrayList<>();
			for (String key : missingFields) {
				Object filled = aiFields.get(key);
				boolean filledBlank = filled == null || (filled instanceof String s && s.isBlank());
				if (filledBlank) {
					remaining.add(key);
				}
			}
			this.missingFields = remaining;
			this.complete = remaining.isEmpty() && validationErrors.isEmpty();
		}
	}

	public UUID getDocumentRequestId() {
		return documentRequestId;
	}

	public UUID getCaseId() {
		return caseId;
	}

	public UUID getTemplateId() {
		return templateId;
	}

	public UUID getSubmittedBy() {
		return submittedBy;
	}

	public Map<String, Object> getData() {
		return data == null ? Map.of() : Map.copyOf(data);
	}

	public Map<String, Object> getAiStructured() {
		return aiStructured;
	}

	public List<String> getMissingFields() {
		return List.copyOf(missingFields);
	}

	public List<String> getValidationErrors() {
		return List.copyOf(validationErrors);
	}

	public SubmissionStatus getStatus() {
		return status;
	}

	public int getVersion() {
		return version;
	}

	public boolean isComplete() {
		return complete;
	}
}
