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
		return toView(template);
	}

	/** code 의 최신 version 템플릿. @throws BusinessException NOT_FOUND */
	@Transactional(readOnly = true)
	public FormTemplateView getByCode(String code) {
		FormTemplate template = templates.findTopByCodeOrderByVersionDesc(code)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "템플릿을 찾을 수 없습니다: " + code));
		return toView(template);
	}

	private FormTemplateView toView(FormTemplate template) {
		return new FormTemplateView(
				template.getId(), template.getCode(), template.getName(),
				template.getVersion(), template.getFields());
	}
}
