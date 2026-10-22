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

	@Test void rejectsWrongIssuerEvenWithTrustedKey() {
		var foreign=new JwtAccessTokenIssuer(signingKey,"other-issuer",Duration.ofMinutes(15),fixedClock(NOW));
		assertThatThrownBy(() -> issuer.parse(foreign.issue(UUID.randomUUID(),Role.CLAIMANT).value()))
			.isInstanceOf(AccessTokenIssuer.InvalidTokenException.class);
	}

	@Test void rejectsExpirationExactlyNow() {
		String token=issuer.issue(UUID.randomUUID(),Role.CLAIMANT).value();
		var verifier=new JwtAccessTokenIssuer(signingKey,ISSUER,Duration.ofMinutes(15),fixedClock(NOW.plusSeconds(900)));
		assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(AccessTokenIssuer.InvalidTokenException.class);
	}

	@Test void rejectsFutureIssuedToken() {
		var future=new JwtAccessTokenIssuer(signingKey,ISSUER,Duration.ofMinutes(15),fixedClock(NOW.plusSeconds(60)));
		assertThatThrownBy(() -> issuer.parse(future.issue(UUID.randomUUID(),Role.CLAIMANT).value()))
			.isInstanceOf(AccessTokenIssuer.InvalidTokenException.class);
	}

	@Test void rejectsUnsupportedAlgorithmAndNotBefore() throws Exception {
		var claims=new com.nimbusds.jwt.JWTClaimsSet.Builder()
			.subject(UUID.randomUUID().toString()).issuer(ISSUER)
			.issueTime(java.util.Date.from(NOW)).expirationTime(java.util.Date.from(NOW.plusSeconds(900)))
			.claim("roles",java.util.List.of("ROLE_CLAIMANT"));
		var wrongAlgorithm=new SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS512),claims.build());
		wrongAlgorithm.sign(new com.nimbusds.jose.crypto.RSASSASigner(signingKey));
		assertThatThrownBy(() -> issuer.parse(wrongAlgorithm.serialize())).isInstanceOf(AccessTokenIssuer.InvalidTokenException.class);
		var premature=new SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256),
			claims.notBeforeTime(java.util.Date.from(NOW.plusSeconds(30))).build());
		premature.sign(new com.nimbusds.jose.crypto.RSASSASigner(signingKey));
		assertThatThrownBy(() -> issuer.parse(premature.serialize())).isInstanceOf(AccessTokenIssuer.InvalidTokenException.class);
	}

	@Test void rejectsMalformedSubjectWithoutEcho() throws Exception {
		var claims=new com.nimbusds.jwt.JWTClaimsSet.Builder().subject("SENTINEL_PATIENT")
			.issuer(ISSUER).issueTime(java.util.Date.from(NOW))
			.expirationTime(java.util.Date.from(NOW.plusSeconds(900)))
			.claim("roles",java.util.List.of("ROLE_CLAIMANT")).build();
		var token=new SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256),claims);
		token.sign(new com.nimbusds.jose.crypto.RSASSASigner(signingKey));
		assertThatThrownBy(() -> issuer.parse(token.serialize()))
			.isInstanceOf(AccessTokenIssuer.InvalidTokenException.class).hasMessageNotContaining("SENTINEL");
	}

	@Test void rejectsMissingOrUnknownRolesAndMissingIssueTime() throws Exception {
		for (java.util.List<String> roles : java.util.List.of(java.util.List.<String>of(),
			java.util.List.of("ROLE_SUPERUSER"),java.util.List.of("ROLE_CLAIMANT","ROLE_ADMIN"))) {
			var claims=new com.nimbusds.jwt.JWTClaimsSet.Builder().subject(UUID.randomUUID().toString())
				.issuer(ISSUER).issueTime(java.util.Date.from(NOW))
				.expirationTime(java.util.Date.from(NOW.plusSeconds(900))).claim("roles",roles).build();
			var token=new SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256),claims);
			token.sign(new com.nimbusds.jose.crypto.RSASSASigner(signingKey));
			assertThatThrownBy(() -> issuer.parse(token.serialize())).isInstanceOf(AccessTokenIssuer.InvalidTokenException.class);
		}
		var missingTime=new com.nimbusds.jwt.JWTClaimsSet.Builder().subject(UUID.randomUUID().toString())
			.issuer(ISSUER).expirationTime(java.util.Date.from(NOW.plusSeconds(900)))
			.claim("roles",java.util.List.of("ROLE_CLAIMANT")).build();
		var token=new SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256),missingTime);
		token.sign(new com.nimbusds.jose.crypto.RSASSASigner(signingKey));
		assertThatThrownBy(() -> issuer.parse(token.serialize())).isInstanceOf(AccessTokenIssuer.InvalidTokenException.class);
	}

	@Test
	@DisplayName("청구 단위 토큰은 case_id를 담고 10분 뒤 만료되며, 로그인 토큰에는 case_id가 없다")
	void claimTokenCarriesCaseIdAndShortLifetime() {
		UUID accountId = UUID.randomUUID();
		UUID caseId = UUID.randomUUID();

		IssuedToken claim = issuer.issueForClaim(accountId, Role.CLAIMANT, caseId);

		assertThat(claim.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
		ParsedToken parsed = issuer.parse(claim.value());
		assertThat(parsed.accountId()).isEqualTo(accountId);
		assertThat(parsed.caseId()).isEqualTo(caseId);
		assertThat(issuer.parse(issuer.issue(accountId, Role.CLAIMANT).value()).caseId()).isNull();
	}
}
