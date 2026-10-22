package com.insuranceai.form.domain;

import com.insuranceai.common.domain.BaseEntity;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.form.infrastructure.FormFieldListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 미리 정의된 "칸" 구조. code + version 으로 식별하며, 같은 code 를 다시 만들면 version 이 올라간다.
 */
@Entity
@Table(name = "form_templates")
public class FormTemplate extends BaseEntity {

	@Column(nullable = false, length = 100)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private int version;

	@Convert(converter = FormFieldListConverter.class)
	@Column(nullable = false, length = 8000)
	private List<FormField> fields = new ArrayList<>();

	protected FormTemplate() {
	}

	public static FormTemplate create(String code, String name, List<FormField> fields, int version) {
		if (fields == null || fields.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "필드는 1개 이상이어야 합니다.");
		}
		Set<String> keys = new HashSet<>();
		for (FormField field : fields) {
			if (field.key() == null || field.key().isBlank()) {
				throw new BusinessException(ErrorCode.INVALID_INPUT, "필드 key 는 필수입니다.");
			}
			if (!keys.add(field.key())) {
				throw new BusinessException(ErrorCode.INVALID_INPUT, "중복된 필드 key: " + field.key());
			}
		}
		FormTemplate template = new FormTemplate();
		template.assignId();
		template.code = requireText(code, "code");
		template.name = requireText(name, "name");
		template.fields = new ArrayList<>(fields);
		template.version = version <= 0 ? 1 : version;
		return template;
	}

	public Set<String> fieldKeys() {
		Set<String> keys = new HashSet<>();
		for (FormField field : fields) {
			keys.add(field.key());
		}
		return keys;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public int getVersion() {
		return version;
	}

	public List<FormField> getFields() {
		return List.copyOf(fields);
	}
}
