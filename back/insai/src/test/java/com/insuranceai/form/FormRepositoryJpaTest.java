package com.insuranceai.form;

import com.insuranceai.form.domain.FormField;
import com.insuranceai.form.domain.FormSubmission;
import com.insuranceai.form.domain.FormSubmissionRepository;
import com.insuranceai.form.domain.FormTemplate;
import com.insuranceai.form.domain.FormTemplateRepository;
import com.insuranceai.form.domain.SubmissionStatus;
import com.insuranceai.support.AbstractJpaTest;
import com.insuranceai.support.fixture.FormFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FormRepositoryJpaTest extends AbstractJpaTest {

	@Autowired
	FormTemplateRepository templates;

	@Autowired
	FormSubmissionRepository submissions;

	@Test
	@DisplayName("FormTemplate: fields JSON 왕복 + 같은 code 최신 version 조회")
	void templateJsonAndLatestVersion() {
		templates.save(FormTemplate.create("medical-cert", "v1", FormFixtures.fields(), 1));
		templates.saveAndFlush(FormTemplate.create("medical-cert", "v2", FormFixtures.fields(), 2));

		FormTemplate latest = templates.findTopByCodeOrderByVersionDesc("medical-cert").orElseThrow();

		assertThat(latest.getVersion()).isEqualTo(2);
		assertThat(latest.getFields()).extracting(FormField::key)
				.containsExactly("hospital", "visitDate", "cost", "category");
	}

	@Test
	@DisplayName("FormSubmission: data/missingFields JSON 왕복 + 조회")
	void submissionJsonAndQueries() {
		UUID docReq = UUID.randomUUID();
		UUID caseId = UUID.randomUUID();
		FormSubmission s = FormSubmission.create(docReq, caseId, UUID.randomUUID(), UUID.randomUUID());
		s.submit(FormFixtures.template(), Map.of("hospital", "서울병원"));
		submissions.saveAndFlush(s);

		FormSubmission loaded = submissions.findByDocumentRequestId(docReq).orElseThrow();

		assertThat(loaded.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
		assertThat(loaded.getData()).containsEntry("hospital", "서울병원");
		assertThat(loaded.getMissingFields()).containsExactly("visitDate");
		assertThat(loaded.isComplete()).isFalse();
		assertThat(submissions.findByCaseId(caseId)).hasSize(1);
	}
}
