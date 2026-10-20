package com.insuranceai.ai.api;

import com.insuranceai.ai.application.*;
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
    public ApiResponse<RunJevService.AnalysisView> run(@RequestHeader("X-User-Id") UUID userId,
                                                    @Valid @RequestBody RunJevCommand command) {
        return ApiResponse.ok(service.run(userId, command));
    }
    @GetMapping("/{analysisId}")
    public ApiResponse<RunJevService.AnalysisView> get(@RequestHeader("X-User-Id") UUID userId,
                                                    @PathVariable UUID analysisId) {
        return ApiResponse.ok(service.get(userId, analysisId));
    }
}
