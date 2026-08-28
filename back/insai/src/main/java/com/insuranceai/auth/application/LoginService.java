package com.insuranceai.auth.application;

import com.insuranceai.auth.domain.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

	private final AccountRepository accounts;
	private final PasswordHasher passwordHasher;
	private final AccessTokenIssuer accessTokenIssuer;
	private final RefreshTokenStore refreshTokenStore;

	public LoginService(AccountRepository accounts,
	                    PasswordHasher passwordHasher,
	                    AccessTokenIssuer accessTokenIssuer,
	                    RefreshTokenStore refreshTokenStore) {
		this.accounts = accounts;
		this.passwordHasher = passwordHasher;
		this.accessTokenIssuer = accessTokenIssuer;
		this.refreshTokenStore = refreshTokenStore;
	}

	/**
	 * @throws com.insuranceai.common.error.BusinessException UNAUTHORIZED - 이메일/비밀번호 불일치,
	 *         FORBIDDEN - PENDING/SUSPENDED 계정
	 */
	@Transactional
	public TokenPair login(LoginCommand command) {
		throw new UnsupportedOperationException("TODO: implement LoginService.login");
	}
}
