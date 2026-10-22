package com.insuranceai.form.catalog;

import com.insuranceai.form.domain.FormTemplate;
import com.insuranceai.form.domain.FormTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 생명보험 청구 서류 템플릿을 부팅 시 1회 시드한다.
 * code 기준 멱등 — 최신 버전의 필드가 카탈로그와 같으면 건너뛰고, 다르면 다음 버전을 만든다.
 */
@Component
@Order(1)
public class LifeInsuranceTemplateSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(LifeInsuranceTemplateSeeder.class);

	private final FormTemplateRepository templates;

	public LifeInsuranceTemplateSeeder(FormTemplateRepository templates) {
		this.templates = templates;
	}

	@Override
	public void run(ApplicationArguments args) {
		seed();
	}

	@Transactional
	public int seed() {
		int created = 0;
		for (FormTemplate template : LifeInsuranceDocumentCatalog.allTemplates()) {
			var latest = templates.findTopByCodeOrderByVersionDesc(template.getCode());
			if (latest.isPresent() && latest.get().getFields().equals(template.getFields())) {
				continue;
			}
			int version = latest.map(t -> t.getVersion() + 1).orElse(1);
			templates.save(FormTemplate.create(template.getCode(), template.getName(), template.getFields(), version));
			created++;
		}
		if (created > 0) {
			log.info("생명보험 청구 서류 템플릿 {}건 시드 완료", created);
		}
		return created;
	}
}
