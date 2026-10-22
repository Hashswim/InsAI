package com.insuranceai.ai.application;

import com.insuranceai.auth.application.AccessTokenIssuer;
import com.insuranceai.auth.application.AuthenticateAccessTokenService.Actor;
import com.insuranceai.auth.domain.Role;
import com.insuranceai.collab.application.CaseAccessService;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** 케이스 참여자에게 그 청구에만 쓸 수 있는 짧은 수명 JWT를 발급한다. */
@Service
public class IssueClaimTokenService {
    private final CaseAccessService access;
    private final AccessTokenIssuer tokens;

    public IssueClaimTokenService(CaseAccessService access, AccessTokenIssuer tokens) {
        this.access = access;
        this.tokens = tokens;
    }

    public AccessTokenIssuer.IssuedToken issue(Actor actor, UUID caseId) {
        // 청구 단위 토큰으로 다시 발급받으면 만료 없이 이어 쓸 수 있으므로 로그인 토큰만 허용한다.
        if (actor.claimScoped()) throw new BusinessException(ErrorCode.FORBIDDEN);
        access.requireParticipant(caseId, actor.userId());
        Role role = Role.valueOf(actor.authority().substring("ROLE_".length()));
        return tokens.issueForClaim(actor.userId(), role, caseId);
    }
}
