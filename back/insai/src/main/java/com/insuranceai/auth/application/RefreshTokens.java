package com.insuranceai.auth.application;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 불투명 리프레시 토큰 생성기와 TTL.
 */
final class RefreshTokens {

	static final Duration TTL = Duration.ofDays(7);

	private static final SecureRandom RANDOM = new SecureRandom();

	private RefreshTokens() {
	}

	static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
