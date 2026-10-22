package com.insuranceai.ai.api;

import com.insuranceai.ai.application.IssueClaimTokenService;
import com.insuranceai.auth.application.AccessTokenIssuer;
import com.insuranceai.auth.application.AuthenticateAccessTokenService.Actor;
import com.insuranceai.common.web.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class JevClaimTokenController {
    private final IssueClaimTokenService service;
    public JevClaimTokenController(IssueClaimTokenService service) { this.service=service; }

    @PostMapping("/ai/jev/cases/{caseId}/claim-token")
    public ApiResponse<AccessTokenIssuer.IssuedToken> issue(@RequestAttribute("jevActor") Actor actor,
                                                           @PathVariable UUID caseId) {
        return ApiResponse.ok(service.issue(actor, caseId));
    }
}
