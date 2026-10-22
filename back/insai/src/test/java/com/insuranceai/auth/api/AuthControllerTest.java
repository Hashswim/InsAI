package com.insuranceai.auth.api;

import com.insuranceai.auth.application.LoginService;
import com.insuranceai.auth.application.LogoutService;
import com.insuranceai.auth.application.SignupService;
import com.insuranceai.auth.application.TokenPair;
import com.insuranceai.auth.application.TokenRefreshService;
import com.insuranceai.common.config.SecurityConfig;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	SignupService signupService;

	@MockitoBean
	LoginService loginService;

	@MockitoBean
	TokenRefreshService tokenRefreshService;

	@MockitoBean
	LogoutService logoutService;

	@Test
	@DisplayName("POST /auth/signup 유효 요청 -> 201, success=true")
	void signupCreated() throws Exception {
		given(signupService.signup(any())).willReturn(UUID.randomUUID());

		mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
						{"email":"u@e.com","password":"password123","name":"홍길동"}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	@DisplayName("POST /auth/signup 이메일 형식 오류 -> 400 INVALID_INPUT")
	void signupInvalidEmail() throws Exception {
		mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
						{"email":"not-an-email","password":"password123","name":"홍길동"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("POST /auth/signup 비밀번호 8자 미만 -> 400")
	void signupShortPassword() throws Exception {
		mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
						{"email":"u@e.com","password":"short","name":"홍길동"}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /auth/signup 이름 공백 -> 400")
	void signupBlankName() throws Exception {
		mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
						{"email":"u@e.com","password":"password123","name":"  "}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /auth/signup 서비스가 CONFLICT -> 409")
	void signupConflict() throws Exception {
		given(signupService.signup(any())).willThrow(new BusinessException(ErrorCode.CONFLICT));

		mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
						{"email":"u@e.com","password":"password123","name":"홍길동"}"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("CONFLICT"));
	}

	@Test
	@DisplayName("POST /auth/login 유효 -> 200, data.accessToken")
	void loginOk() throws Exception {
		given(loginService.login(any())).willReturn(new TokenPair("acc-token", "ref-token", 900));

		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
						{"email":"u@e.com","password":"password123"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").value("acc-token"))
				.andExpect(jsonPath("$.data.refreshToken").value("ref-token"))
				.andExpect(jsonPath("$.data.expiresIn").value(900));
	}

	@Test
	@DisplayName("POST /auth/login 잘못된 자격 -> 401")
	void loginUnauthorized() throws Exception {
		given(loginService.login(any())).willThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
						{"email":"u@e.com","password":"wrong"}"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("POST /auth/refresh 무효 토큰 -> 401")
	void refreshUnauthorized() throws Exception {
		given(tokenRefreshService.refresh(any())).willThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

		mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("""
						{"refreshToken":"bad"}"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("POST /auth/refresh 유효 -> 200, 새 토큰")
	void refreshOk() throws Exception {
		given(tokenRefreshService.refresh(any())).willReturn(new TokenPair("new-acc", "new-ref", 900));

		mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("""
						{"refreshToken":"ref-token"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").value("new-acc"));
	}

	@Test
	@DisplayName("POST /auth/logout -> 200")
	void logoutOk() throws Exception {
		mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content("""
						{"refreshToken":"ref-token"}"""))
				.andExpect(status().isOk());
	}
}
