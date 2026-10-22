package com.insuranceai.auth.application;

import com.insuranceai.auth.domain.AccountRepository;
import com.insuranceai.auth.domain.AccountStatus;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthenticateAccessTokenService {
    private final AccessTokenIssuer tokens;
    private final AccountRepository accounts;

    public AuthenticateAccessTokenService(AccessTokenIssuer tokens, AccountRepository accounts) {
        this.tokens = tokens;
        this.accounts = accounts;
    }

    public record Actor(UUID userId, String authority) {}

    @Transactional(readOnly = true)
    public Actor authenticate(String token) {
        AccessTokenIssuer.ParsedToken parsed;
        try {
            parsed = tokens.parse(token);
        } catch (AccessTokenIssuer.InvalidTokenException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        var account = accounts.findById(parsed.accountId())
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (account.getStatus() != AccountStatus.ACTIVE
            || !Set.of(account.getRole().authority()).equals(parsed.roles())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return new Actor(account.getId(), account.getRole().authority());
    }
}
