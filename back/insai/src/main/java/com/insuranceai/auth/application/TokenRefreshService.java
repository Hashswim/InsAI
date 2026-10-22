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
import java.util.UUID;

@Service
public class TokenRefreshService {

	private final AccountRepository accounts;
	private final AccessTokenIssuer accessTokenIssuer;
	private final RefreshTokenStore refreshTokenStore;

	public TokenRefreshService(AccountRepository accounts,
	                           AccessTokenIssuer accessTokenIssuer,
	                           RefreshTokenStore refreshTokenStore) {
		this.accounts = accounts;
		this.accessTokenIssuer = accessTokenIssuer;
		this.refreshTokenStore = refreshTokenStore;
	}

	/**
	 * 리프레시 토큰을 회전한다: 새 토큰쌍을 발급하고 기존 리프레시 토큰은 폐기한다.
	 *
	 * @throws BusinessException UNAUTHORIZED - 미존재/만료/이미 사용된 리프레시 토큰
	 */
	@Transactional
	public TokenPair refresh(String refreshToken) {
		UUID accountId = refreshTokenStore.findAccountId(refreshToken)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."));
		Account account = accounts.findById(accountId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."));

		refreshTokenStore.delete(refreshToken);

		IssuedToken access = accessTokenIssuer.issue(account.getId(), account.getRole());
		String rotated = RefreshTokens.newToken();
		refreshTokenStore.save(rotated, account.getId(), RefreshTokens.TTL);

		long expiresIn = Duration.between(Instant.now(), access.expiresAt()).toSeconds();
		return new TokenPair(access.value(), rotated, expiresIn);
	}
}
