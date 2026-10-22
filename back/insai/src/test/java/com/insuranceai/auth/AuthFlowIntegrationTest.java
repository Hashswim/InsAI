package com.insuranceai.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper om;

	@Test
	@DisplayName("가입 -> 로그인 -> 갱신 -> 로그아웃 -> 폐기된 refresh 재사용은 401")
	void fullFlow() throws Exception {
		signup("flow@e.com", "password123", "홍길동", null).andExpect(status().isCreated());

		String loginBody = perform(post("/auth/login"), """
				{"email":"flow@e.com","password":"password123"}""")
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String refreshToken = json(loginBody).path("data").path("refreshToken").asText();
		assertThat(refreshToken).isNotBlank();

		String refreshBody = perform(post("/auth/refresh"), """
				{"refreshToken":"%s"}""".formatted(refreshToken))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String rotated = json(refreshBody).path("data").path("refreshToken").asText();
		assertThat(rotated).isNotBlank().isNotEqualTo(refreshToken);

		perform(post("/auth/logout"), """
				{"refreshToken":"%s"}""".formatted(rotated)).andExpect(status().isOk());

		perform(post("/auth/refresh"), """
				{"refreshToken":"%s"}""".formatted(rotated)).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("잘못된 비밀번호 로그인 -> 401")
	void wrongPassword() throws Exception {
		signup("wrong@e.com", "password123", "홍길동", null).andExpect(status().isCreated());

		perform(post("/auth/login"), """
				{"email":"wrong@e.com","password":"nope"}""")
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("ADJUSTER 가입 후 승인 전 로그인 -> 403")
	void adjusterPendingCannotLogin() throws Exception {
		signup("adj@e.com", "password123", "김사정", "ADJUSTER").andExpect(status().isCreated());

		perform(post("/auth/login"), """
				{"email":"adj@e.com","password":"password123"}""")
				.andExpect(status().isForbidden());
	}

	private org.springframework.test.web.servlet.ResultActions signup(
			String email, String password, String name, String role) throws Exception {
		String roleField = role == null ? "" : ",\"role\":\"%s\"".formatted(role);
		return perform(post("/auth/signup"), """
				{"email":"%s","password":"%s","name":"%s"%s}""".formatted(email, password, name, roleField));
	}

	private org.springframework.test.web.servlet.ResultActions perform(
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String body)
			throws Exception {
		return mvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private JsonNode json(String body) throws Exception {
		return om.readTree(body);
	}
}
