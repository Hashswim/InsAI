package com.insuranceai.auth.api;

import com.insuranceai.auth.application.TokenPair;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {

	public static TokenResponse from(TokenPair pair) {
		return new TokenResponse(pair.accessToken(), pair.refreshToken(), pair.expiresInSeconds());
	}
}
