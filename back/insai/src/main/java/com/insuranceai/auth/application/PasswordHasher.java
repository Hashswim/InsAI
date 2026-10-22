package com.insuranceai.auth.application;

/**
 * 비밀번호 해시 포트. 구현은 infrastructure (BCrypt).
 */
public interface PasswordHasher {

	String hash(String rawPassword);

	boolean matches(String rawPassword, String passwordHash);
}
