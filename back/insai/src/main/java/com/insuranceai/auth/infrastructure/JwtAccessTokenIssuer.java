package com.insuranceai.auth.infrastructure;

import com.insuranceai.auth.application.AccessTokenIssuer;
import com.insuranceai.auth.domain.Role;
import com.nimbusds.jose.jwk.RSAKey;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * RS256 JWT 액세스 토큰 발급/검증.
 *
 * 클레임: sub=accountId, roles=[..], iss, iat, exp
 * 만료: 발급 시각 + ttl (기본 15분)
 */
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

	private final RSAKey rsaKey;
	private final String issuer;
	private final Duration ttl;
	private final Clock clock;

	public JwtAccessTokenIssuer(RSAKey rsaKey, String issuer, Duration ttl, Clock clock) {
		this.rsaKey = rsaKey;
		this.issuer = issuer;
		this.ttl = ttl;
		this.clock = clock;
	}

	@Override
	public IssuedToken issue(UUID accountId, Role role) {
		throw new UnsupportedOperationException("TODO: implement JwtAccessTokenIssuer.issue");
	}

	@Override
	public ParsedToken parse(String token) {
		throw new UnsupportedOperationException("TODO: implement JwtAccessTokenIssuer.parse");
	}
}
