package com.insuranceai.auth.infrastructure;

import com.insuranceai.auth.application.AccessTokenIssuer;
import com.insuranceai.auth.domain.Role;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * RS256 JWT 액세스 토큰 발급/검증.
 *
 * 클레임: sub=accountId, roles=[ROLE_..], iss, iat, exp
 * 만료: 발급 시각(clock) + ttl
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
		Instant now = clock.instant();
		Instant expiresAt = now.plus(ttl);
		try {
			JWSSigner signer = new RSASSASigner(rsaKey);
			JWTClaimsSet claims = new JWTClaimsSet.Builder()
					.subject(accountId.toString())
					.issuer(issuer)
					.issueTime(Date.from(now))
					.expirationTime(Date.from(expiresAt))
					.claim("roles", List.of(role.authority()))
					.build();
			SignedJWT jwt = new SignedJWT(
					new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
					claims);
			jwt.sign(signer);
			return new IssuedToken(jwt.serialize(), expiresAt);
		} catch (JOSEException e) {
			throw new IllegalStateException("액세스 토큰 서명에 실패했습니다.", e);
		}
	}

	@Override
	public ParsedToken parse(String token) {
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			JWSVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
			if (!jwt.verify(verifier)) {
				throw new InvalidTokenException("서명이 유효하지 않습니다.");
			}
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			Date expiration = claims.getExpirationTime();
			if (expiration == null || expiration.toInstant().isBefore(clock.instant())) {
				throw new InvalidTokenException("만료된 토큰입니다.");
			}
			List<String> roles = claims.getStringListClaim("roles");
			Set<String> roleSet = roles == null ? Set.of() : new LinkedHashSet<>(roles);
			return new ParsedToken(UUID.fromString(claims.getSubject()), roleSet);
		} catch (ParseException | JOSEException e) {
			throw new InvalidTokenException("토큰을 해석할 수 없습니다.", e);
		}
	}
}
