package com.insuranceai.ai.api;

import com.insuranceai.ai.application.*;
import com.insuranceai.auth.application.AuthenticateAccessTokenService.Actor;
import com.insuranceai.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/ai/jev")
public class JevController {
    private final RunJevService service;
    public JevController(RunJevService service) { this.service=service; }

    @PostMapping
    public ApiResponse<RunJevService.AnalysisView> run(@RequestAttribute("jevActor") Actor actor,
                                                    @Valid @RequestBody RunJevCommand command,
                                                    @RequestAttribute("jevRequestId") UUID requestId) {
        return ApiResponse.ok(forRole(actor, service.run(actor.userId(), command, requestId)));
    }
    @GetMapping("/cases/{caseId}/latest")
    public ApiResponse<RunJevService.AnalysisView> latest(@RequestAttribute("jevActor") Actor actor,
                                                       @PathVariable UUID caseId,
                                                       @RequestAttribute("jevRequestId") UUID requestId) {
        return ApiResponse.ok(forRole(actor, service.latest(actor.userId(), caseId, requestId)));
    }
    @GetMapping("/{analysisId}")
    public ApiResponse<RunJevService.AnalysisView> get(@RequestAttribute("jevActor") Actor actor,
                                                    @PathVariable UUID analysisId,
                                                    @RequestAttribute("jevRequestId") UUID requestId) {
        return ApiResponse.ok(forRole(actor, service.get(actor.userId(), analysisId, requestId)));
    }

    /** The adjuster-facing summary and risk signals never reach the claimant. */
    private static RunJevService.AnalysisView forRole(Actor actor, RunJevService.AnalysisView view) {
        return view != null && "ROLE_CLAIMANT".equals(actor.authority()) ? view.forClaimant() : view;
    }
}
