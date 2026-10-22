package com.insuranceai.support.fixture;

import com.insuranceai.auth.domain.Account;
import com.insuranceai.auth.domain.Role;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

/**
 * 테스트용 Account 빌더. passwordHash 는 실제 BCrypt "password123" 해시.
 */
public final class AccountFixture {

	/** BCrypt 해시 of "password123" */
	public static final String PASSWORD_123_HASH =
			"$2a$10$7EqJtq98hPqEX7fNZaFWoOa8Rn9k1kJ2Yl0mE0m1sB0qYcQ0mQe6.";

	private AccountFixture() {
	}

	public static Account claimant(String email) {
		return Account.create(email, PASSWORD_123_HASH, "테스트청구인", Role.CLAIMANT);
	}

	public static Account adjuster(String email) {
		return Account.create(email, PASSWORD_123_HASH, "테스트사정사", Role.ADJUSTER);
	}

	/** 영속 상태를 흉내내기 위해 id 를 강제로 채운 인스턴스 */
	public static Account withId(UUID id, String email, Role role) {
		Account account = Account.create(email, PASSWORD_123_HASH, "테스트", role);
		ReflectionTestUtils.setField(account, "id", id);
		return account;
	}
}
