package com.insuranceai.auth.application;

import com.insuranceai.auth.domain.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	 * 리프레시 토큰을 회전(rotate)한다: 새 토큰쌍을 발급하고 기존 리프레시 토큰은 폐기한다.
	 *
	 * @throws com.insuranceai.common.error.BusinessException UNAUTHORIZED - 미존재/만료/이미 사용된 리프레시 토큰
	 */
	@Transactional
	public TokenPair refresh(String refreshToken) {
		throw new UnsupportedOperationException("TODO: implement TokenRefreshService.refresh");
	}
}
