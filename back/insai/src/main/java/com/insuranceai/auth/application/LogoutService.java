package com.insuranceai.auth.application;

import org.springframework.stereotype.Service;

@Service
public class LogoutService {

	private final RefreshTokenStore refreshTokenStore;

	public LogoutService(RefreshTokenStore refreshTokenStore) {
		this.refreshTokenStore = refreshTokenStore;
	}

	/** 리프레시 토큰을 폐기한다. 이미 없으면 조용히 성공(멱등). */
	public void logout(String refreshToken) {
		throw new UnsupportedOperationException("TODO: implement LogoutService.logout");
	}
}
