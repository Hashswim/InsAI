package com.insuranceai.ai.api;

import com.insuranceai.ai.application.*;
import com.insuranceai.auth.application.AuthenticateAccessTokenService.Actor;
import com.insuranceai.common.error.*;
import com.insuranceai.common.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/jev/jobs")
public class JevJobController {
    private final JevJobService service;
    public JevJobController(JevJobService service) { this.service=service; }
    @PostMapping
    public ResponseEntity<ApiResponse<JevJobService.JobView>> submit(@RequestAttribute("jevActor") Actor actor,
            @RequestAttribute("jevRequestId") UUID requestId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RunJevCommand command) {
        actor.requireCase(command.caseId());
        UUID parsed;
        try {
            parsed=UUID.fromString(key);
            if (!parsed.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.INVALID_INPUT); }
        var job=service.submit(actor.userId(),parsed,requestId,command);
        return ResponseEntity.accepted().header("Location","/ai/jev/jobs/"+job.jobId()).body(ApiResponse.ok(job));
    }
    @GetMapping("/{jobId}")
    public ApiResponse<JevJobService.JobView> get(@RequestAttribute("jevActor") Actor actor, @PathVariable UUID jobId,
            @RequestAttribute("jevRequestId") UUID requestId) {
        return ApiResponse.ok(service.get(actor.userId(),jobId,requestId));
    }
}
