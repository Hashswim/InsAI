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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenRefreshServiceTest {

	@Mock
	AccountRepository accounts;

	@Mock
	AccessTokenIssuer accessTokenIssuer;

	@Mock
	RefreshTokenStore refreshTokenStore;

	@InjectMocks
	TokenRefreshService tokenRefreshService;

	@Test
	@DisplayName("유효한 refresh 토큰: 새 TokenPair 발급하고 기존 토큰은 폐기(회전)한다")
	void rotatesRefreshToken() {
		UUID accountId = UUID.randomUUID();
		Account account = AccountFixture.withId(accountId, "u@e.com", Role.CLAIMANT);
		given(refreshTokenStore.findAccountId("refresh-old")).willReturn(Optional.of(accountId));
		given(accounts.findById(accountId)).willReturn(Optional.of(account));
		given(accessTokenIssuer.issue(eq(accountId), eq(Role.CLAIMANT)))
				.willReturn(new IssuedToken("new-access", Instant.now().plusSeconds(900)));

		TokenPair pair = tokenRefreshService.refresh("refresh-old");

		assertThat(pair.accessToken()).isEqualTo("new-access");
		assertThat(pair.refreshToken()).isNotBlank().isNotEqualTo("refresh-old");
		verify(refreshTokenStore).delete("refresh-old");
		verify(refreshTokenStore).save(anyString(), eq(accountId), any());
	}

	@Test
	@DisplayName("존재하지 않거나 만료된 refresh 토큰이면 UNAUTHORIZED")
	void unknownRefreshToken() {
		given(refreshTokenStore.findAccountId("bad")).willReturn(Optional.empty());

		assertThatThrownBy(() -> tokenRefreshService.refresh("bad"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.UNAUTHORIZED);
	}

	@Test
	@DisplayName("회전되어 이미 폐기된 refresh 토큰 재사용은 UNAUTHORIZED")
	void reusedRefreshTokenRejected() {
		given(refreshTokenStore.findAccountId("refresh-old")).willReturn(Optional.empty());

		assertThatThrownBy(() -> tokenRefreshService.refresh("refresh-old"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.UNAUTHORIZED);
	}
}
