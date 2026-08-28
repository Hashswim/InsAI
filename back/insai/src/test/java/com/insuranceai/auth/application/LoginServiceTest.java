package com.insuranceai.auth.application;

import com.insuranceai.auth.application.AccessTokenIssuer.IssuedToken;
import com.insuranceai.auth.domain.Account;
import com.insuranceai.auth.domain.AccountRepository;
import com.insuranceai.auth.domain.Role;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.support.fixture.AccountFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

	@Mock
	AccountRepository accounts;

	@Mock
	PasswordHasher passwordHasher;

	@Mock
	AccessTokenIssuer accessTokenIssuer;

	@Mock
	RefreshTokenStore refreshTokenStore;

	@InjectMocks
	LoginService loginService;

	@Test
	@DisplayName("정상 로그인: TokenPair 를 반환하고 refresh 토큰을 저장한다")
	void loginSucceeds() {
		UUID accountId = UUID.randomUUID();
		Account account = AccountFixture.withId(accountId, "u@e.com", Role.CLAIMANT);
		given(accounts.findByEmail("u@e.com")).willReturn(Optional.of(account));
		given(passwordHasher.matches("password123", account.getPasswordHash())).willReturn(true);
		given(accessTokenIssuer.issue(eq(accountId), eq(Role.CLAIMANT)))
				.willReturn(new IssuedToken("access-jwt", Instant.now().plusSeconds(900)));

		TokenPair pair = loginService.login(new LoginCommand("u@e.com", "password123"));

		assertThat(pair.accessToken()).isEqualTo("access-jwt");
		assertThat(pair.refreshToken()).isNotBlank();
		verify(refreshTokenStore).save(anyString(), eq(accountId), any(Duration.class));
	}

	@Test
	@DisplayName("존재하지 않는 이메일이면 UNAUTHORIZED")
	void unknownEmail() {
		given(accounts.findByEmail("nobody@e.com")).willReturn(Optional.empty());

		assertThatThrownBy(() -> loginService.login(new LoginCommand("nobody@e.com", "password123")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.UNAUTHORIZED);

		verify(refreshTokenStore, never()).save(anyString(), any(), any());
	}

	@Test
	@DisplayName("비밀번호가 틀리면 UNAUTHORIZED")
	void wrongPassword() {
		Account account = AccountFixture.withId(UUID.randomUUID(), "u@e.com", Role.CLAIMANT);
		given(accounts.findByEmail("u@e.com")).willReturn(Optional.of(account));
		given(passwordHasher.matches("wrong", account.getPasswordHash())).willReturn(false);

		assertThatThrownBy(() -> loginService.login(new LoginCommand("u@e.com", "wrong")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.UNAUTHORIZED);
	}

	@Test
	@DisplayName("승인 대기(PENDING) 사정사 계정은 FORBIDDEN")
	void pendingAccountForbidden() {
		Account adjuster = AccountFixture.withId(UUID.randomUUID(), "adj@e.com", Role.ADJUSTER);
		given(accounts.findByEmail("adj@e.com")).willReturn(Optional.of(adjuster));
		given(passwordHasher.matches("password123", adjuster.getPasswordHash())).willReturn(true);

		assertThatThrownBy(() -> loginService.login(new LoginCommand("adj@e.com", "password123")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.FORBIDDEN);
	}
}
