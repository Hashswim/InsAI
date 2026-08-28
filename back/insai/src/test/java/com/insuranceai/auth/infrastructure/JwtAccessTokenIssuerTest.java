package com.insuranceai.auth.infrastructure;

import com.insuranceai.auth.application.AccessTokenIssuer;
import com.insuranceai.auth.application.AccessTokenIssuer.IssuedToken;
import com.insuranceai.auth.application.AccessTokenIssuer.ParsedToken;
import com.insuranceai.auth.domain.Role;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAccessTokenIssuerTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final String ISSUER = "insai-test";

	private RSAKey signingKey;
	private RSAKey otherKey;
	private JwtAccessTokenIssuer issuer;

	@BeforeEach
	void setUp() throws Exception {
		signingKey = new RSAKeyGenerator(2048).keyID("test-key-1").generate();
		otherKey = new RSAKeyGenerator(2048).keyID("other-key").generate();
		issuer = new JwtAccessTokenIssuer(signingKey, ISSUER, Duration.ofMinutes(15), fixedClock(NOW));
	}

	@Test
	@DisplayName("발급 토큰에 sub / roles / iss / exp 클레임이 담긴다")
	void issuesTokenWithClaims() throws Exception {
		UUID accountId = UUID.randomUUID();

		IssuedToken token = issuer.issue(accountId, Role.CLAIMANT);

		var claims = SignedJWT.parse(token.value()).getJWTClaimsSet();
		assertThat(claims.getSubject()).isEqualTo(accountId.toString());
		assertThat(claims.getStringListClaim("roles")).contains("ROLE_CLAIMANT");
		assertThat(claims.getIssuer()).isEqualTo(ISSUER);
		assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
		assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
	}

	@Test
	@DisplayName("유효한 토큰은 parse 로 accountId 와 roles 를 복원한다")
	void parsesValidToken() {
		UUID accountId = UUID.randomUUID();
		String value = issuer.issue(accountId, Role.ADJUSTER).value();

		ParsedToken parsed = issuer.parse(value);

		assertThat(parsed.accountId()).isEqualTo(accountId);
		assertThat(parsed.roles()).contains("ROLE_ADJUSTER");
	}

	@Test
	@DisplayName("만료된 토큰은 InvalidTokenException")
	void rejectsExpiredToken() {
		String value = issuer.issue(UUID.randomUUID(), Role.CLAIMANT).value();
		JwtAccessTokenIssuer laterIssuer = new JwtAccessTokenIssuer(
				signingKey, ISSUER, Duration.ofMinutes(15), fixedClock(NOW.plus(Duration.ofMinutes(16))));

		assertThatThrownBy(() -> laterIssuer.parse(value))
				.isInstanceOf(AccessTokenIssuer.InvalidTokenException.class);
	}

	@Test
	@DisplayName("서명이 변조된 토큰은 InvalidTokenException")
	void rejectsTamperedToken() {
		String value = issuer.issue(UUID.randomUUID(), Role.CLAIMANT).value();
		String tampered = value.substring(0, value.length() - 2)
				+ (value.endsWith("a") ? "bb" : "aa");

		assertThatThrownBy(() -> issuer.parse(tampered))
				.isInstanceOf(AccessTokenIssuer.InvalidTokenException.class);
	}

	@Test
	@DisplayName("다른 키로 서명된 토큰은 InvalidTokenException")
	void rejectsTokenSignedWithOtherKey() {
		JwtAccessTokenIssuer foreignIssuer =
				new JwtAccessTokenIssuer(otherKey, ISSUER, Duration.ofMinutes(15), fixedClock(NOW));
		String value = foreignIssuer.issue(UUID.randomUUID(), Role.CLAIMANT).value();

		assertThatThrownBy(() -> issuer.parse(value))
				.isInstanceOf(AccessTokenIssuer.InvalidTokenException.class);
	}

	private static Clock fixedClock(Instant instant) {
		return Clock.fixed(instant, ZoneOffset.UTC);
	}
}
