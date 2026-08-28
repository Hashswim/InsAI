package com.insuranceai.auth.api;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 공개키 배포. 게이트웨이/각 서비스가 이 엔드포인트로 액세스 토큰 서명을 검증한다.
 * 개인키 성분은 절대 노출하지 않는다 (toPublicJWK).
 */
@RestController
public class JwksController {

	private final RSAKey rsaKey;

	public JwksController(RSAKey rsaKey) {
		this.rsaKey = rsaKey;
	}

	@GetMapping("/.well-known/jwks.json")
	public Map<String, Object> jwks() {
		return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
	}
}
