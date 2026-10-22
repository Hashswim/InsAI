package com.insuranceai.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * document 모듈 설정. localDir — 로컬 디스크 스토리지 루트, publicBaseUrl — 발급 URL 접두어.
 */
@ConfigurationProperties("insai.storage")
public record DocumentProperties(String localDir, String publicBaseUrl) {

	public DocumentProperties {
		if (localDir == null || localDir.isBlank()) {
			localDir = "./.local-storage";
		}
		if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
			publicBaseUrl = "/local-storage";
		}
	}
}
