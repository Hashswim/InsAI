package com.insuranceai.form.api;

import com.insuranceai.common.config.SecurityConfig;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.form.application.CreateTemplateService;
import com.insuranceai.form.application.FormTemplateView;
import com.insuranceai.form.application.GetTemplateService;
import com.insuranceai.form.domain.FieldType;
import com.insuranceai.form.domain.FormField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FormTemplateController.class)
@Import(SecurityConfig.class)
class FormTemplateControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean CreateTemplateService createTemplateService;
	@MockitoBean GetTemplateService getTemplateService;

	private static final String ID = "44444444-4444-4444-4444-444444444444";
	private static final String FIELDS =
			"[{\"key\":\"hospital\",\"label\":\"병원명\",\"type\":\"TEXT\",\"required\":true}]";

	@Test
	@DisplayName("POST /templates -> 201, data.templateId")
	void create() throws Exception {
		given(createTemplateService.create(any())).willReturn(UUID.fromString(ID));

		mvc.perform(post("/templates").contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"medical\",\"name\":\"진단서\",\"fields\":" + FIELDS + "}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.templateId").value(ID));
	}

	@Test
	@DisplayName("POST /templates code 공백 -> 400")
	void createBlankCode() throws Exception {
		mvc.perform(post("/templates").contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"  \",\"name\":\"진단서\",\"fields\":" + FIELDS + "}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /templates fields 비어있음 -> 400")
	void createEmptyFields() throws Exception {
		mvc.perform(post("/templates").contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"medical\",\"name\":\"진단서\",\"fields\":[]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /templates 서비스가 INVALID_INPUT -> 400")
	void createServiceRejects() throws Exception {
		given(createTemplateService.create(any())).willThrow(new BusinessException(ErrorCode.INVALID_INPUT));

		mvc.perform(post("/templates").contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"medical\",\"name\":\"진단서\",\"fields\":" + FIELDS + "}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("GET /templates/{id} -> 200, 스키마")
	void getTemplate() throws Exception {
		given(getTemplateService.get(any())).willReturn(new FormTemplateView(
				UUID.fromString(ID), "medical", "진단서", 1,
				List.of(new FormField("hospital", "병원명", FieldType.TEXT, true, null, null, null, null))));

		mvc.perform(get("/templates/{id}", ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.code").value("medical"))
				.andExpect(jsonPath("$.data.fields[0].key").value("hospital"));
	}

	@Test
	@DisplayName("GET /templates/{id} 없음 -> 404")
	void getNotFound() throws Exception {
		given(getTemplateService.get(any())).willThrow(new BusinessException(ErrorCode.NOT_FOUND));

		mvc.perform(get("/templates/{id}", ID)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("GET /templates?code= -> 200")
	void getByCode() throws Exception {
		given(getTemplateService.getByCode("life.death-certificate")).willReturn(new FormTemplateView(
				UUID.fromString(ID), "life.death-certificate", "사망진단서(시체검안서)", 1,
				List.of(new FormField("deceasedName", "사망자 성명", FieldType.TEXT, true, null, null, null, null))));

		mvc.perform(get("/templates").param("code", "life.death-certificate"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.code").value("life.death-certificate"))
				.andExpect(jsonPath("$.data.fields[0].key").value("deceasedName"));
	}

	@Test
	@DisplayName("GET /templates?code= 없음 -> 404")
	void getByCodeNotFound() throws Exception {
		given(getTemplateService.getByCode(any())).willThrow(new BusinessException(ErrorCode.NOT_FOUND));

		mvc.perform(get("/templates").param("code", "life.nope"))
				.andExpect(status().isNotFound());
	}
}
