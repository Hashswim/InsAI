package com.insuranceai.ai.api;

import com.insuranceai.ai.application.JevMetricsService;
import com.insuranceai.auth.application.AuthenticateAccessTokenService.Actor;
import com.insuranceai.common.web.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/jev/cases/{caseId}/metrics")
public class JevMetricsController {
    private final JevMetricsService service;
    public JevMetricsController(JevMetricsService service) { this.service=service; }
    @GetMapping
    public ApiResponse<JevMetricsService.Metrics> get(@RequestAttribute("jevActor") Actor actor,
            @RequestAttribute("jevRequestId") UUID requestId, @PathVariable UUID caseId,
            @RequestParam(defaultValue="7") int days) {
        return ApiResponse.ok(service.get(actor.userId(),caseId,requestId,days));
    }
}
