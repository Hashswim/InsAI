package com.insuranceai.form.domain;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.support.fixture.FormFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormSubmissionTest {

	private final FormTemplate template = FormFixtures.template();

	private FormSubmission newSubmission() {
		return FormSubmission.create(UUID.randomUUID(), UUID.randomUUID(), template.getId(), UUID.randomUUID());
	}

	@Test
	@DisplayName("submit: 템플릿에 없는 key 포함이면 INVALID_INPUT")
	void unknownKey() {
		assertThatThrownBy(() -> newSubmission().submit(template, Map.of("없는필드", "x")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("submit: 필수 누락 - 제출은 되지만 missingFields 채워지고 complete=false")
	void requiredMissing() {
		FormSubmission s = newSubmission();

		s.submit(template, Map.of("hospital", "서울병원"));

		assertThat(s.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
		assertThat(s.getMissingFields()).containsExactly("visitDate");
		assertThat(s.isComplete()).isFalse();
		assertThat(s.getVersion()).isEqualTo(1);
	}

	@Test
	@DisplayName("submit: 타입 불일치는 validationErrors 로")
	void typeMismatch() {
		FormSubmission s = newSubmission();

		s.submit(template, Map.of("hospital", "서울", "visitDate", "2026-01-01", "cost", "공짜"));

		assertThat(s.getValidationErrors()).anyMatch(e -> e.startsWith("cost"));
		assertThat(s.isComplete()).isFalse();
	}

	@Test
	@DisplayName("submit: 필수 모두 채우면 complete=true")
	void complete() {
		FormSubmission s = newSubmission();

		s.submit(template, Map.of("hospital", "서울", "visitDate", "2026-01-01"));

		assertThat(s.isComplete()).isTrue();
		assertThat(s.getMissingFields()).isEmpty();
	}

	@Test
	@DisplayName("submit: 재제출 시 version 증가")
	void resubmitBumpsVersion() {
		FormSubmission s = newSubmission();

		s.submit(template, Map.of("hospital", "A", "visitDate", "2026-01-01"));
		s.submit(template, Map.of("hospital", "B", "visitDate", "2026-01-02"));

		assertThat(s.getVersion()).isEqualTo(2);
	}

	@Test
	@DisplayName("applyAiStructured: AI 가 채운 누락 필드는 missingFields 에서 제거된다")
	void applyAiStructured() {
		FormSubmission s = newSubmission();
		s.submit(template, Map.of("hospital", "서울"));

		s.applyAiStructured(Map.of("visitDate", "2026-01-01"), List.of());

		assertThat(s.getMissingFields()).isEmpty();
		assertThat(s.isComplete()).isTrue();
		assertThat(s.getAiStructured()).containsEntry("visitDate", "2026-01-01");
	}

	@Test
	@DisplayName("saveDraft: 상태 DRAFT 유지, 부분 데이터 보관")
	void saveDraft() {
		FormSubmission s = newSubmission();

		s.saveDraft(Map.of("hospital", "서울"));

		assertThat(s.getStatus()).isEqualTo(SubmissionStatus.DRAFT);
		assertThat(s.getData()).containsEntry("hospital", "서울");
	}
}
