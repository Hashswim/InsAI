package com.insuranceai.auth.application;

import com.insuranceai.auth.domain.Account;
import com.insuranceai.auth.domain.AccountRepository;
import com.insuranceai.auth.domain.AccountStatus;
import com.insuranceai.auth.domain.Role;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

	@Mock
	AccountRepository accounts;

	@Mock
	PasswordHasher passwordHasher;

	@InjectMocks
	SignupService signupService;

	@Test
	@DisplayName("정상 가입: 해시된 비밀번호로 저장하고 id 를 반환한다")
	void signsUp() {
		given(accounts.existsByEmail("u@e.com")).willReturn(false);
		given(passwordHasher.hash("password123")).willReturn("HASHED");
		given(accounts.save(any(Account.class))).willAnswer(inv -> inv.getArgument(0));

		UUID id = signupService.signup(new SignupCommand("u@e.com", "password123", "홍길동", Role.CLAIMANT));

		assertThat(id).isNotNull();
		ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
		verify(accounts).save(captor.capture());
		assertThat(captor.getValue().getPasswordHash()).isEqualTo("HASHED");
		assertThat(captor.getValue().getEmail()).isEqualTo("u@e.com");
	}

	@Test
	@DisplayName("이미 존재하는 이메일이면 CONFLICT 이고 저장하지 않는다")
	void rejectsDuplicateEmail() {
		given(accounts.existsByEmail("u@e.com")).willReturn(true);

		assertThatThrownBy(() ->
				signupService.signup(new SignupCommand("u@e.com", "password123", "홍길동", Role.CLAIMANT)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.CONFLICT);

		verify(accounts, never()).save(any());
	}

	@Test
	@DisplayName("ADJUSTER 가입은 PENDING 상태로 저장된다")
	void adjusterSignupIsPending() {
		given(accounts.existsByEmail(any())).willReturn(false);
		given(passwordHasher.hash(any())).willReturn("HASHED");
		given(accounts.save(any(Account.class))).willAnswer(inv -> inv.getArgument(0));

		signupService.signup(new SignupCommand("adj@e.com", "password123", "김사정", Role.ADJUSTER));

		ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
		verify(accounts).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(AccountStatus.PENDING);
	}

	@Test
	@DisplayName("role 이 null 이면 CLAIMANT 로 가입된다")
	void nullRoleDefaultsToClaimant() {
		given(accounts.existsByEmail(any())).willReturn(false);
		given(passwordHasher.hash(any())).willReturn("HASHED");
		given(accounts.save(any(Account.class))).willAnswer(inv -> inv.getArgument(0));

		signupService.signup(new SignupCommand("u@e.com", "password123", "홍길동", null));

		ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
		verify(accounts).save(captor.capture());
		assertThat(captor.getValue().getRole()).isEqualTo(Role.CLAIMANT);
	}
}
