package com.insuranceai.form.application;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.form.domain.FormTemplate;
import com.insuranceai.form.domain.FormTemplateRepository;
import com.insuranceai.support.fixture.FormFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreateTemplateServiceTest {

	@Mock
	FormTemplateRepository templates;

	@InjectMocks
	CreateTemplateService service;

	@Test
	@DisplayName("새 code 는 version 1 로 저장된다")
	void newCode() {
		given(templates.findTopByCodeOrderByVersionDesc("medical-cert")).willReturn(Optional.empty());
		given(templates.save(any())).willAnswer(i -> i.getArgument(0));

		service.create(new CreateTemplateCommand("medical-cert", "진단서", FormFixtures.fields()));

		ArgumentCaptor<FormTemplate> captor = ArgumentCaptor.forClass(FormTemplate.class);
		verify(templates).save(captor.capture());
		assertThat(captor.getValue().getVersion()).isEqualTo(1);
	}

	@Test
	@DisplayName("이미 있는 code 는 version 을 올려 저장한다")
	void existingCode() {
		given(templates.findTopByCodeOrderByVersionDesc("medical-cert"))
				.willReturn(Optional.of(FormFixtures.template()));
		given(templates.save(any())).willAnswer(i -> i.getArgument(0));

		service.create(new CreateTemplateCommand("medical-cert", "진단서 개정", FormFixtures.fields()));

		ArgumentCaptor<FormTemplate> captor = ArgumentCaptor.forClass(FormTemplate.class);
		verify(templates).save(captor.capture());
		assertThat(captor.getValue().getVersion()).isEqualTo(2);
	}

	@Test
	@DisplayName("필드 0개면 도메인 검증으로 INVALID_INPUT")
	void emptyFields() {
		given(templates.findTopByCodeOrderByVersionDesc(any())).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(new CreateTemplateCommand("c", "n", List.of())))
				.isInstanceOf(BusinessException.class);
	}
}
