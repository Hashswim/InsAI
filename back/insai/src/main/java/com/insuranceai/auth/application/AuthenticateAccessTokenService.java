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

    /** caseId가 있으면 청구 단위 토큰으로 인증된 것이고, 그 케이스에만 접근할 수 있다. */
    public record Actor(UUID userId, String authority, UUID caseId) {
        public Actor(UUID userId, String authority) { this(userId, authority, null); }

        public boolean claimScoped() { return caseId != null; }

        public void requireCase(UUID requested) {
            if (caseId != null && !caseId.equals(requested)) throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

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
        return new Actor(account.getId(), account.getRole().authority(), parsed.caseId());
    }
}
