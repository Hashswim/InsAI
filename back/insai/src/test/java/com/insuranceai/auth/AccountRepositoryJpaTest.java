package com.insuranceai.auth;

import com.insuranceai.auth.domain.Account;
import com.insuranceai.auth.domain.AccountRepository;
import com.insuranceai.support.AbstractJpaTest;
import com.insuranceai.support.fixture.AccountFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountRepositoryJpaTest extends AbstractJpaTest {

	@Autowired
	AccountRepository accounts;

	@Test
	@DisplayName("save 후 findByEmail 로 조회된다")
	void savesAndFindsByEmail() {
		accounts.save(AccountFixture.claimant("u@e.com"));

		assertThat(accounts.findByEmail("u@e.com")).isPresent();
		assertThat(accounts.findByEmail("nobody@e.com")).isEmpty();
	}

	@Test
	@DisplayName("existsByEmail")
	void existsByEmail() {
		accounts.save(AccountFixture.claimant("u@e.com"));

		assertThat(accounts.existsByEmail("u@e.com")).isTrue();
		assertThat(accounts.existsByEmail("other@e.com")).isFalse();
	}

	@Test
	@DisplayName("이메일 unique 제약 위반 시 DataIntegrityViolationException")
	void uniqueEmailConstraint() {
		accounts.saveAndFlush(AccountFixture.claimant("dup@e.com"));

		assertThatThrownBy(() -> accounts.saveAndFlush(AccountFixture.claimant("dup@e.com")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("createdAt / updatedAt 이 JPA Auditing 으로 자동 세팅된다")
	void auditingTimestamps() {
		Account saved = accounts.saveAndFlush(AccountFixture.claimant("u@e.com"));

		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
	}
}
