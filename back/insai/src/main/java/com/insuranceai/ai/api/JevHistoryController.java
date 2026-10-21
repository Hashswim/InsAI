package com.insuranceai.ai.api;

import com.insuranceai.ai.application.JevHistoryService;
import com.insuranceai.auth.application.AuthenticateAccessTokenService.Actor;
import com.insuranceai.common.web.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/jev")
public class JevHistoryController {
    private final JevHistoryService service;
    public JevHistoryController(JevHistoryService service) { this.service=service; }
    @GetMapping("/{analysisId}/history")
    public ApiResponse<List<JevHistoryService.EventView>> analysis(@RequestAttribute("jevActor") Actor actor,
            @RequestAttribute("jevRequestId") UUID requestId, @PathVariable UUID analysisId) {
        return ApiResponse.ok(service.history(actor.userId(),analysisId,requestId,false));
    }
    @GetMapping("/jobs/{jobId}/history")
    public ApiResponse<List<JevHistoryService.EventView>> job(@RequestAttribute("jevActor") Actor actor,
            @RequestAttribute("jevRequestId") UUID requestId, @PathVariable UUID jobId) {
        return ApiResponse.ok(service.history(actor.userId(),jobId,requestId,true));
    }
}
