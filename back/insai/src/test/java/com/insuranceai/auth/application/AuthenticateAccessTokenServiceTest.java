package com.insuranceai.auth.application;

import com.insuranceai.auth.domain.*;
import com.insuranceai.common.error.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticateAccessTokenServiceTest {
    private final AccessTokenIssuer tokens=mock(AccessTokenIssuer.class);
    private final AccountRepository accounts=mock(AccountRepository.class);
    private final AuthenticateAccessTokenService service=new AuthenticateAccessTokenService(tokens,accounts);

    @Test void validatesCurrentAccountAndRole() {
        var account=Account.create("test@test.invalid","hash","test",Role.CLAIMANT);
        when(tokens.parse("token")).thenReturn(new AccessTokenIssuer.ParsedToken(account.getId(),Set.of("ROLE_CLAIMANT")));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        assertEquals(account.getId(),service.authenticate("token").userId());
        account.suspend();
        assertEquals(ErrorCode.UNAUTHORIZED,assertThrows(BusinessException.class,
            () -> service.authenticate("token")).getErrorCode());
    }

    @Test void rejectsStaleRole() {
        var account=Account.create("test@test.invalid","hash","test",Role.CLAIMANT);
        when(tokens.parse("token")).thenReturn(new AccessTokenIssuer.ParsedToken(account.getId(),Set.of("ROLE_ADJUSTER")));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        assertThrows(BusinessException.class,() -> service.authenticate("token"));
    }

    @Test void tokenFailureDoesNotExposeCredentialsOrCallDatabase() {
        when(tokens.parse("SENTINEL_TOKEN")).thenThrow(new AccessTokenIssuer.InvalidTokenException("SENTINEL_TOKEN"));
        var error=assertThrows(BusinessException.class,() -> service.authenticate("SENTINEL_TOKEN"));
        assertEquals(ErrorCode.UNAUTHORIZED,error.getErrorCode());
        assertFalse(error.getMessage().contains("SENTINEL"));
        verifyNoInteractions(accounts);
    }

    @Test void deletedAccountCannotAuthenticate() {
        var id=java.util.UUID.randomUUID();
        when(tokens.parse("token")).thenReturn(new AccessTokenIssuer.ParsedToken(id,Set.of("ROLE_CLAIMANT")));
        when(accounts.findById(id)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.UNAUTHORIZED,assertThrows(BusinessException.class,
            () -> service.authenticate("token")).getErrorCode());
    }
}
