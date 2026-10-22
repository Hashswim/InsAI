package com.insuranceai.auth.domain;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class AccountTest {

	@Test
	@DisplayName("이메일을 소문자·trim 으로 정규화해 저장한다")
	void normalizesEmail() {
		Account account = Account.create("  User@Example.COM  ", "hash", "홍길동", Role.CLAIMANT);

		assertThat(account.getEmail()).isEqualTo("user@example.com");
	}

	@Test
	@DisplayName("role 미지정 시 CLAIMANT / ACTIVE 로 생성된다")
	void defaultsToClaimantActive() {
		Account account = Account.create("u@e.com", "hash", "홍길동", null);

		assertThat(account.getRole()).isEqualTo(Role.CLAIMANT);
		assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	@DisplayName("ADJUSTER 는 승인 대기(PENDING) 상태로 생성된다")
	void adjusterStartsPending() {
		Account account = Account.create("adj@e.com", "hash", "김사정", Role.ADJUSTER);

		assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING);
	}

	@Test
	@DisplayName("빈 이메일이면 INVALID_INPUT")
	void rejectsBlankEmail() {
		assertThatThrownBy(() -> Account.create("   ", "hash", "홍길동", Role.CLAIMANT))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("activate: PENDING -> ACTIVE")
	void activate() {
		Account account = Account.create("adj@e.com", "hash", "김사정", Role.ADJUSTER);

		account.activate();

		assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	@DisplayName("suspend: ACTIVE -> SUSPENDED")
	void suspend() {
		Account account = Account.create("u@e.com", "hash", "홍길동", Role.CLAIMANT);

		account.suspend();

		assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
	}

	@Test
	@DisplayName("changePassword: 해시를 교체한다")
	void changePassword() {
		Account account = Account.create("u@e.com", "hash-1", "홍길동", Role.CLAIMANT);

		account.changePassword("hash-2");

		assertThat(account.getPasswordHash()).isEqualTo("hash-2");
	}

	@Test
	@DisplayName("verifyActive: PENDING 이면 FORBIDDEN")
	void verifyActiveRejectsPending() {
		Account account = Account.create("adj@e.com", "hash", "김사정", Role.ADJUSTER);

		assertThatThrownBy(account::verifyActive)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("verifyActive: ACTIVE 이면 통과")
	void verifyActivePassesForActive() {
		Account account = Account.create("u@e.com", "hash", "홍길동", Role.CLAIMANT);

		assertThatCode(account::verifyActive).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Role.authority(): ROLE_ 접두사")
	void roleAuthority() {
		assertThat(Role.CLAIMANT.authority()).isEqualTo("ROLE_CLAIMANT");
		assertThat(Role.ADJUSTER.authority()).isEqualTo("ROLE_ADJUSTER");
	}
}
