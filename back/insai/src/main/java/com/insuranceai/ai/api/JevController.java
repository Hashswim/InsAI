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
        return ApiResponse.ok(service.run(actor.userId(), command, requestId));
    }
    @GetMapping("/{analysisId}")
    public ApiResponse<RunJevService.AnalysisView> get(@RequestAttribute("jevActor") Actor actor,
                                                    @PathVariable UUID analysisId,
                                                    @RequestAttribute("jevRequestId") UUID requestId) {
        return ApiResponse.ok(service.get(actor.userId(), analysisId, requestId));
    }
}
