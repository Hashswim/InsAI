package com.insuranceai.form.catalog;

import com.insuranceai.form.domain.FieldType;
import com.insuranceai.form.domain.FormField;
import com.insuranceai.form.domain.FormTemplate;
import com.insuranceai.form.domain.FormTemplateRepository;
import com.insuranceai.support.AbstractJpaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LifeInsuranceTemplateSeederTest extends AbstractJpaTest {

	@Autowired
	FormTemplateRepository templates;

	@Test
	@DisplayName("빈 상태에서 15건 시드하고, 재실행 시 멱등(0건)")
	void seedsThenIdempotent() {
		// 공유 H2 에 다른 @SpringBootTest 컨텍스트의 시드가 커밋돼 있을 수 있어 트랜잭션 내에서 비운다(롤백됨).
		templates.deleteAll();
		LifeInsuranceTemplateSeeder seeder = new LifeInsuranceTemplateSeeder(templates);

		int firstRun = seeder.seed();
		int secondRun = seeder.seed();

		assertThat(firstRun).isEqualTo(15);
		assertThat(secondRun).isZero();
		assertThat(templates.count()).isEqualTo(15);
		assertThat(templates.findTopByCodeOrderByVersionDesc("life.death-certificate")).isPresent();
		assertThat(templates.findTopByCodeOrderByVersionDesc("life.claim-application")
				.orElseThrow().getFields()).extracting(FormField::type).containsExactly(FieldType.FILE, FieldType.TEXT);
	}

	@Test
	@DisplayName("예전 입력 칸 템플릿이 있으면 첨부 + 문의 사항 다음 버전을 만든다")
	void upgradesChangedTemplate() {
		templates.deleteAll();
		templates.save(FormTemplate.create("life.diagnosis-certificate", "진단서", List.of(
				new FormField("diseaseCode", "질병분류기호", FieldType.TEXT, true, null, null, null, null)), 1));
		LifeInsuranceTemplateSeeder seeder = new LifeInsuranceTemplateSeeder(templates);

		assertThat(seeder.seed()).isEqualTo(15);

		FormTemplate latest = templates.findTopByCodeOrderByVersionDesc("life.diagnosis-certificate").orElseThrow();
		assertThat(latest.getVersion()).isEqualTo(2);
		assertThat(latest.getFields()).extracting(FormField::type).containsExactly(FieldType.FILE, FieldType.TEXT);
	}
}
