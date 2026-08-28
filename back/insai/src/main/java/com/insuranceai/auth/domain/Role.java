package com.insuranceai.auth.domain;

/**
 * 사용자 역할. CLAIMANT(청구인), ADJUSTER(손해사정사), ADMIN(운영자).
 */
public enum Role {

	CLAIMANT,
	ADJUSTER,
	ADMIN;

	public String authority() {
		return "ROLE_" + name();
	}
}
