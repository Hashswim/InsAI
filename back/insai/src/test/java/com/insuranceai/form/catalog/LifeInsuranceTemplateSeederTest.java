package com.insuranceai.form.catalog;

import com.insuranceai.form.domain.FormTemplateRepository;
import com.insuranceai.support.AbstractJpaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
				.orElseThrow().getFields()).isNotEmpty();
	}
}
