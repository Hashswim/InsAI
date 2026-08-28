package com.insuranceai.auth.api;

import com.insuranceai.auth.application.LoginCommand;
import com.insuranceai.auth.application.LoginService;
import com.insuranceai.auth.application.LogoutService;
import com.insuranceai.auth.application.SignupCommand;
import com.insuranceai.auth.application.SignupService;
import com.insuranceai.auth.application.TokenRefreshService;
import com.insuranceai.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final SignupService signupService;
	private final LoginService loginService;
	private final TokenRefreshService tokenRefreshService;
	private final LogoutService logoutService;

	public AuthController(SignupService signupService,
	                     LoginService loginService,
	                     TokenRefreshService tokenRefreshService,
	                     LogoutService logoutService) {
		this.signupService = signupService;
		this.loginService = loginService;
		this.tokenRefreshService = tokenRefreshService;
		this.logoutService = logoutService;
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
		signupService.signup(new SignupCommand(
				request.email(), request.password(), request.name(), request.role()));
		return ApiResponse.ok();
	}

	@PostMapping("/login")
	public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.ok(TokenResponse.from(
				loginService.login(new LoginCommand(request.email(), request.password()))));
	}

	@PostMapping("/refresh")
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return ApiResponse.ok(TokenResponse.from(
				tokenRefreshService.refresh(request.refreshToken())));
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
		logoutService.logout(request.refreshToken());
		return ApiResponse.ok();
	}
}
