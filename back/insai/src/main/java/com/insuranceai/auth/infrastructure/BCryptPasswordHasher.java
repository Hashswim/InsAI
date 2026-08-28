package com.insuranceai.auth.infrastructure;

import com.insuranceai.auth.application.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

	private final PasswordEncoder encoder = new BCryptPasswordEncoder();

	@Override
	public String hash(String rawPassword) {
		return encoder.encode(rawPassword);
	}

	@Override
	public boolean matches(String rawPassword, String passwordHash) {
		return encoder.matches(rawPassword, passwordHash);
	}
}
