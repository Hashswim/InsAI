package com.insuranceai.form.application;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.form.domain.FormTemplate;
import com.insuranceai.form.domain.FormTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetTemplateService {

	private final FormTemplateRepository templates;

	public GetTemplateService(FormTemplateRepository templates) {
		this.templates = templates;
	}

	/** @throws BusinessException NOT_FOUND - 없는 템플릿 */
	@Transactional(readOnly = true)
	public FormTemplateView get(UUID templateId) {
		FormTemplate template = templates.findById(templateId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "템플릿을 찾을 수 없습니다."));
		return new FormTemplateView(
				template.getId(), template.getCode(), template.getName(),
				template.getVersion(), template.getFields());
	}
}
