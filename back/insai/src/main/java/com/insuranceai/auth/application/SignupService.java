package com.insuranceai.auth.application;

import com.insuranceai.auth.domain.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SignupService {

	private final AccountRepository accounts;
	private final PasswordHasher passwordHasher;

	public SignupService(AccountRepository accounts, PasswordHasher passwordHasher) {
		this.accounts = accounts;
		this.passwordHasher = passwordHasher;
	}

	/**
	 * @return 생성된 계정 id
	 * @throws com.insuranceai.common.error.BusinessException CONFLICT - 이미 존재하는 이메일
	 */
	@Transactional
	public UUID signup(SignupCommand command) {
		throw new UnsupportedOperationException("TODO: implement SignupService.signup");
	}
}
