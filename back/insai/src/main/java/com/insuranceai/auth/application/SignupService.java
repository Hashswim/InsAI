package com.insuranceai.auth.application;

import com.insuranceai.auth.domain.Account;
import com.insuranceai.auth.domain.AccountRepository;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
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
	 * @throws BusinessException CONFLICT - 이미 존재하는 이메일
	 */
	@Transactional
	public UUID signup(SignupCommand command) {
		String email = normalize(command.email());
		if (email != null && accounts.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 이메일입니다.");
		}
		String passwordHash = passwordHasher.hash(command.rawPassword());
		Account account = Account.create(command.email(), passwordHash, command.name(), command.role());
		return accounts.save(account).getId();
	}

	private static String normalize(String email) {
		return email == null ? null : email.trim().toLowerCase();
	}
}
