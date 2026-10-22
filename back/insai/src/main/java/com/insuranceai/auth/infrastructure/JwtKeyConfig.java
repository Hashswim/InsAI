package com.insuranceai.auth.infrastructure;

import com.insuranceai.auth.application.AccessTokenIssuer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * JWT 서명 키와 발급기 빈 구성.
 *
 * TODO: 현재는 부팅 시마다 RSA 키를 새로 생성한다(재시작 시 기존 토큰 무효).
 *       운영에서는 시크릿/키스토어에서 로드하도록 교체.
 */
@Configuration
public class JwtKeyConfig {

	private static final String ISSUER = "insai-auth";
	private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

	@Bean
	public RSAKey rsaKey() {
		try {
			return new RSAKeyGenerator(2048)
					.keyID("insai-auth-1")
					.generate();
		} catch (JOSEException e) {
			throw new IllegalStateException("RSA 키 생성 실패", e);
		}
	}

	@Bean
	public AccessTokenIssuer accessTokenIssuer(RSAKey rsaKey) {
		return new JwtAccessTokenIssuer(rsaKey, ISSUER, ACCESS_TOKEN_TTL, Clock.systemUTC());
	}
}
