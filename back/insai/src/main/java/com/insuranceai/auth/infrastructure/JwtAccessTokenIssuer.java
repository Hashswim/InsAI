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
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * RS256 JWT 액세스 토큰 발급/검증.
 *
 * 클레임: sub=accountId, roles=[ROLE_..], iss, iat, exp (청구 단위 토큰은 case_id 추가)
 * 만료: 발급 시각(clock) + ttl, 청구 단위 토큰은 + CLAIM_TOKEN_TTL
 */
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

	static final Duration CLAIM_TOKEN_TTL = Duration.ofMinutes(10);

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
		return sign(accountId, role, null, ttl);
	}

	@Override
	public IssuedToken issueForClaim(UUID accountId, Role role, UUID caseId) {
		return sign(accountId, role, caseId, CLAIM_TOKEN_TTL);
	}

	private IssuedToken sign(UUID accountId, Role role, UUID caseId, Duration lifetime) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(lifetime);
		try {
			JWSSigner signer = new RSASSASigner(rsaKey);
			JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
					.subject(accountId.toString())
					.issuer(issuer)
					.issueTime(Date.from(now))
					.expirationTime(Date.from(expiresAt))
					.claim("roles", List.of(role.authority()));
			if (caseId != null) builder.claim("case_id", caseId.toString());
			JWTClaimsSet claims = builder.build();
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
			if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
				throw new InvalidTokenException("허용되지 않는 서명 알고리즘입니다.");
			}
			JWSVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
			if (!jwt.verify(verifier)) {
				throw new InvalidTokenException("서명이 유효하지 않습니다.");
			}
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			Instant now = clock.instant();
			if (!issuer.equals(claims.getIssuer())) throw new InvalidTokenException("발급자가 유효하지 않습니다.");
			Date expiration = claims.getExpirationTime();
			if (expiration == null || !expiration.toInstant().isAfter(now)) {
				throw new InvalidTokenException("만료된 토큰입니다.");
			}
			Date issuedAt = claims.getIssueTime();
			if (issuedAt == null || issuedAt.toInstant().isAfter(now)
					|| !issuedAt.before(expiration)
					|| (claims.getNotBeforeTime() != null && claims.getNotBeforeTime().toInstant().isAfter(now))) {
				throw new InvalidTokenException("토큰 시간이 유효하지 않습니다.");
			}
			List<String> roles = claims.getStringListClaim("roles");
			if (roles == null || roles.size() != 1
					|| java.util.Arrays.stream(Role.values()).noneMatch(role -> role.authority().equals(roles.getFirst()))) {
				throw new InvalidTokenException("역할이 유효하지 않습니다.");
			}
			UUID accountId = UUID.fromString(claims.getSubject());
			if (!accountId.toString().equals(claims.getSubject())) throw new IllegalArgumentException();
			String caseClaim = claims.getStringClaim("case_id");
			UUID caseId = caseClaim == null ? null : UUID.fromString(caseClaim);
			if (caseId != null && !caseId.toString().equals(caseClaim)) throw new IllegalArgumentException();
			return new ParsedToken(accountId, Set.copyOf(roles), caseId);
		} catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
			throw new InvalidTokenException("토큰을 해석할 수 없습니다.");
		}
	}
}
