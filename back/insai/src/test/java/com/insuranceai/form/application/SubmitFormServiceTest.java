package com.insuranceai.form.application;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.form.application.AiStructurePort.AiStructureResult;
import com.insuranceai.form.domain.FormSubmission;
import com.insuranceai.form.domain.FormSubmissionRepository;
import com.insuranceai.form.domain.FormTemplate;
import com.insuranceai.form.domain.FormTemplateRepository;
import com.insuranceai.support.fixture.FormFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubmitFormServiceTest {

	@Mock FormTemplateRepository templates;
	@Mock FormSubmissionRepository submissions;
	@Mock AiStructurePort aiStructurePort;
	@Mock DocumentRequestSubmissionPort documentRequestSubmissionPort;

	@InjectMocks
	SubmitFormService service;

	private final FormTemplate template = FormFixtures.template();
	private final UUID docReq = UUID.randomUUID();
	private final UUID caseId = UUID.randomUUID();
	private final UUID by = UUID.randomUUID();

	private static final AiStructureResult EMPTY_AI =
			new AiStructureResult(Map.of(), Map.of(), List.of());

	private Map<String, Object> validData() {
		return Map.of("hospital", "서울병원", "visitDate", "2026-01-01");
	}

	@Test
	@DisplayName("제출 성립 시 submission 저장 + collab 포트 호출")
	void submitsAndNotifiesCollab() {
		given(templates.findById(any())).willReturn(Optional.of(template));
		given(submissions.findByDocumentRequestId(docReq)).willReturn(Optional.empty());
		given(aiStructurePort.structure(any(), any())).willReturn(EMPTY_AI);
		given(submissions.save(any())).willAnswer(i -> i.getArgument(0));

		UUID id = service.submit(new SubmitFormCommand(docReq, caseId, template.getId(), by, validData()));

		assertThat(id).isNotNull();
		verify(documentRequestSubmissionPort).onFormSubmitted(docReq, by);
	}

	@Test
	@DisplayName("템플릿이 없으면 NOT_FOUND")
	void templateNotFound() {
		given(templates.findById(any())).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.submit(
				new SubmitFormCommand(docReq, caseId, UUID.randomUUID(), by, Map.of())))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("AI 구조화가 실패해도 제출은 성공한다")
	void aiFailureTolerated() {
		given(templates.findById(any())).willReturn(Optional.of(template));
		given(submissions.findByDocumentRequestId(any())).willReturn(Optional.empty());
		given(aiStructurePort.structure(any(), any())).willThrow(new RuntimeException("LLM 타임아웃"));
		given(submissions.save(any())).willAnswer(i -> i.getArgument(0));

		UUID id = service.submit(new SubmitFormCommand(docReq, caseId, template.getId(), by, validData()));

		assertThat(id).isNotNull();
		verify(documentRequestSubmissionPort).onFormSubmitted(any(), any());
	}

	@Test
	@DisplayName("documentRequestId 가 없으면 collab 포트를 호출하지 않는다")
	void noDocumentRequestSkipsCollab() {
		given(templates.findById(any())).willReturn(Optional.of(template));
		given(aiStructurePort.structure(any(), any())).willReturn(EMPTY_AI);
		given(submissions.save(any())).willAnswer(i -> i.getArgument(0));

		service.submit(new SubmitFormCommand(null, caseId, template.getId(), by, validData()));

		verify(documentRequestSubmissionPort, never()).onFormSubmitted(any(), any());
	}

	@Test
	@DisplayName("getByDocumentRequestId: 제출물이 있으면 뷰, 없으면 NOT_FOUND")
	void getByDocumentRequestId() {
		com.insuranceai.form.application.GetSubmissionService getService =
				new com.insuranceai.form.application.GetSubmissionService(submissions);
		FormSubmission existing = FormSubmission.create(docReq, caseId, template.getId(), by);
		given(submissions.findByDocumentRequestId(docReq)).willReturn(Optional.of(existing));

		assertThat(getService.getByDocumentRequestId(docReq).status()).isEqualTo("DRAFT");
		assertThatThrownBy(() -> getService.getByDocumentRequestId(UUID.randomUUID()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("기존 제출물이 있으면 이어서 재제출(version 증가)")
	void resubmitsExisting() {
		FormSubmission existing = FormSubmission.create(docReq, caseId, template.getId(), by);
		existing.submit(template, validData());
		given(templates.findById(any())).willReturn(Optional.of(template));
		given(submissions.findByDocumentRequestId(docReq)).willReturn(Optional.of(existing));
		given(aiStructurePort.structure(any(), any())).willReturn(EMPTY_AI);
		given(submissions.save(any())).willAnswer(i -> i.getArgument(0));

		service.submit(new SubmitFormCommand(docReq, caseId, template.getId(), by, validData()));

		assertThat(existing.getVersion()).isEqualTo(2);
	}
}
