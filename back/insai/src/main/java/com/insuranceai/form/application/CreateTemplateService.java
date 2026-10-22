package com.insuranceai.form.application;

import com.insuranceai.form.domain.FormTemplate;
import com.insuranceai.form.domain.FormTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreateTemplateService {

	private final FormTemplateRepository templates;

	public CreateTemplateService(FormTemplateRepository templates) {
		this.templates = templates;
	}

	/**
	 * 같은 code 가 이미 있으면 version 을 올려 새 템플릿으로 저장한다.
	 *
	 * @return 생성된 템플릿 id
	 * @throws com.insuranceai.common.error.BusinessException INVALID_INPUT - 필드 0개/중복 key
	 */
	@Transactional
	public UUID create(CreateTemplateCommand command) {
		int nextVersion = templates.findTopByCodeOrderByVersionDesc(command.code())
				.map(existing -> existing.getVersion() + 1)
				.orElse(1);
		FormTemplate template = FormTemplate.create(
				command.code(), command.name(), command.fields(), nextVersion);
		return templates.save(template).getId();
	}
}
