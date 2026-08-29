package com.insuranceai.dev;

import com.insuranceai.auth.api.TokenResponse;
import com.insuranceai.auth.application.LoginCommand;
import com.insuranceai.auth.application.LoginService;
import com.insuranceai.auth.application.SignupCommand;
import com.insuranceai.auth.application.SignupService;
import com.insuranceai.auth.domain.Account;
import com.insuranceai.auth.domain.AccountRepository;
import com.insuranceai.auth.domain.AccountStatus;
import com.insuranceai.auth.domain.Role;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.common.web.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 개발/데모 편의용. dev 프로파일에서만 노출된다 (test/prod 에는 존재하지 않음).
 * TODO: 정식 로그인/관리자 승인 UI 복원 시 제거.
 */
@RestController
@Profile("dev")
@RequestMapping("/dev")
public class DevController {

	private static final String DEMO_PASSWORD = "demo-password-1234";

	private final AccountRepository accounts;
	private final SignupService signupService;
	private final LoginService loginService;

	public DevController(AccountRepository accounts, SignupService signupService, LoginService loginService) {
		this.accounts = accounts;
		this.signupService = signupService;
		this.loginService = loginService;
	}

	public record ActivateRequest(String email) {
	}

	public record DemoLoginRequest(String role) {
	}

	/** 승인 대기(PENDING) 손해사정사 계정을 ACTIVE 로. */
	@PostMapping("/activate")
	@Transactional
	public ApiResponse<Void> activate(@RequestBody ActivateRequest request) {
		Account account = accounts.findByEmail(request.email().trim().toLowerCase())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "계정을 찾을 수 없습니다."));
		if (account.getStatus() == AccountStatus.PENDING) {
			account.activate();
			accounts.save(account);
		}
		return ApiResponse.ok();
	}

	/**
	 * 자격증명 없이 역할만으로 로그인. 해당 역할의 데모 계정을 없으면 생성·활성화한 뒤 토큰을 발급한다.
	 * body: { "role": "CLAIMANT" | "ADJUSTER" }
	 */
	@PostMapping("/demo-login")
	@Transactional
	public ApiResponse<TokenResponse> demoLogin(@RequestBody DemoLoginRequest request) {
		Role role;
		try {
			role = Role.valueOf(String.valueOf(request.role()).trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "role 은 CLAIMANT 또는 ADJUSTER 여야 합니다.");
		}

		String email = "demo-" + role.name().toLowerCase() + "@insai.local";
		if (accounts.findByEmail(email).isEmpty()) {
			signupService.signup(new SignupCommand(email, DEMO_PASSWORD, demoName(role), role));
		}
		accounts.findByEmail(email).ifPresent(account -> {
			if (account.getStatus() == AccountStatus.PENDING) {
				account.activate();
				accounts.save(account);
			}
		});

		return ApiResponse.ok(TokenResponse.from(loginService.login(new LoginCommand(email, DEMO_PASSWORD))));
	}

	private static String demoName(Role role) {
		return role == Role.ADJUSTER ? "데모 손해사정사" : "데모 청구인";
	}
}
