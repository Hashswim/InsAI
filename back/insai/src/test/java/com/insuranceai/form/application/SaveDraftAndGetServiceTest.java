package com.insuranceai.form.application;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.form.domain.FormSubmission;
import com.insuranceai.form.domain.FormSubmissionRepository;
import com.insuranceai.form.domain.FormTemplate;
import com.insuranceai.form.domain.FormTemplateRepository;
import com.insuranceai.form.domain.SubmissionStatus;
import com.insuranceai.support.fixture.FormFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SaveDraftAndGetServiceTest {

	@Mock
	FormSubmissionRepository submissions;

	@Mock
	FormTemplateRepository templates;

	@InjectMocks
	SaveDraftService saveDraftService;

	@InjectMocks
	GetTemplateService getTemplateService;

	@InjectMocks
	GetSubmissionService getSubmissionService;

	@Test
	@DisplayName("saveDraft: 기존 제출물이 없으면 새 DRAFT 를 생성해 저장")
	void createsNewDraft() {
		given(submissions.findByDocumentRequestId(any())).willReturn(Optional.empty());
		given(submissions.save(any())).willAnswer(i -> i.getArgument(0));

		UUID id = saveDraftService.saveDraft(new SaveDraftCommand(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Map.of("hospital", "서울")));

		assertThat(id).isNotNull();
		ArgumentCaptor<FormSubmission> captor = ArgumentCaptor.forClass(FormSubmission.class);
		verify(submissions).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(SubmissionStatus.DRAFT);
	}

	@Test
	@DisplayName("updateDraft: 없는 제출물이면 NOT_FOUND")
	void updateDraftNotFound() {
		given(submissions.findById(any())).willReturn(Optional.empty());

		assertThatThrownBy(() -> saveDraftService.updateDraft(UUID.randomUUID(), Map.of()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("getTemplate: 없으면 NOT_FOUND")
	void templateNotFound() {
		given(templates.findById(any())).willReturn(Optional.empty());

		assertThatThrownBy(() -> getTemplateService.get(UUID.randomUUID()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("getTemplate: 찾으면 렌더용 뷰(필드 4개) 반환")
	void templateFound() {
		FormTemplate template = FormFixtures.template();
		given(templates.findById(any())).willReturn(Optional.of(template));

		FormTemplateView view = getTemplateService.get(UUID.randomUUID());

		assertThat(view.code()).isEqualTo("medical-cert");
		assertThat(view.fields()).hasSize(4);
	}

	@Test
	@DisplayName("getByCode: code 의 최신 버전을 반환, 없으면 NOT_FOUND")
	void getByCode() {
		given(templates.findTopByCodeOrderByVersionDesc("medical-cert"))
				.willReturn(Optional.of(FormFixtures.template()));
		given(templates.findTopByCodeOrderByVersionDesc("nope")).willReturn(Optional.empty());

		assertThat(getTemplateService.getByCode("medical-cert").code()).isEqualTo("medical-cert");
		assertThatThrownBy(() -> getTemplateService.getByCode("nope"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("getSubmission: 없으면 NOT_FOUND")
	void submissionNotFound() {
		given(submissions.findById(any())).willReturn(Optional.empty());

		assertThatThrownBy(() -> getSubmissionService.get(UUID.randomUUID()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("getSubmission: 찾으면 뷰 반환")
	void submissionFound() {
		FormSubmission submission = FormSubmission.create(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		given(submissions.findById(any())).willReturn(Optional.of(submission));

		FormSubmissionView view = getSubmissionService.get(UUID.randomUUID());

		assertThat(view.status()).isEqualTo("DRAFT");
	}
}
