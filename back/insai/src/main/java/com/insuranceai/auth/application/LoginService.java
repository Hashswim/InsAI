package com.insuranceai.auth.application;

import com.insuranceai.auth.application.AccessTokenIssuer.IssuedToken;
import com.insuranceai.auth.domain.Account;
import com.insuranceai.auth.domain.AccountRepository;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

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
	 * @throws BusinessException UNAUTHORIZED - 이메일/비밀번호 불일치,
	 *                           FORBIDDEN - PENDING/SUSPENDED 계정
	 */
	@Transactional
	public TokenPair login(LoginCommand command) {
		String email = command.email() == null ? "" : command.email().trim().toLowerCase();
		Account account = accounts.findByEmail(email)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

		if (!passwordHasher.matches(command.rawPassword(), account.getPasswordHash())) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
		}
		account.verifyActive();

		IssuedToken access = accessTokenIssuer.issue(account.getId(), account.getRole());
		String refreshToken = RefreshTokens.newToken();
		refreshTokenStore.save(refreshToken, account.getId(), RefreshTokens.TTL);

		long expiresIn = Duration.between(Instant.now(), access.expiresAt()).toSeconds();
		return new TokenPair(access.value(), refreshToken, expiresIn);
	}
}
