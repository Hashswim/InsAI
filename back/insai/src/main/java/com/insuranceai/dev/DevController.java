package com.insuranceai.dev;

import com.insuranceai.auth.domain.Account;
import com.insuranceai.auth.domain.AccountRepository;
import com.insuranceai.auth.domain.AccountStatus;
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
 * 로컬 개발 편의용. dev 프로파일에서만 노출된다 (test/prod 에는 존재하지 않음).
 * TODO: 관리자 승인 콘솔 구현 시 제거.
 */
@RestController
@Profile("dev")
@RequestMapping("/dev")
public class DevController {

	private final AccountRepository accounts;

	public DevController(AccountRepository accounts) {
		this.accounts = accounts;
	}

	public record ActivateRequest(String email) {
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
}
