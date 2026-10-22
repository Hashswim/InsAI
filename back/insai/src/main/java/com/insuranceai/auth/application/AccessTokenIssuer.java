package com.insuranceai.auth.application;

import com.insuranceai.auth.domain.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 액세스 토큰(JWT, RS256) 발급·검증 포트. 구현: infrastructure.JwtAccessTokenIssuer.
 */
public interface AccessTokenIssuer {

	IssuedToken issue(UUID accountId, Role role);

	/** 유효하지 않거나 만료된 토큰이면 InvalidTokenException */
	ParsedToken parse(String token);

	record IssuedToken(String value, Instant expiresAt) {
	}

	record ParsedToken(UUID accountId, Set<String> roles) {
	}

	class InvalidTokenException extends RuntimeException {
		public InvalidTokenException(String message) {
			super(message);
		}

		public InvalidTokenException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
