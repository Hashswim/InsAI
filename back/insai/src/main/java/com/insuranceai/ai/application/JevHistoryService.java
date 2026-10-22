package com.insuranceai.ai.application;

import com.insuranceai.ai.domain.*;
import com.insuranceai.collab.application.CaseAccessService;
import com.insuranceai.common.error.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JevHistoryService {
    private final JevAnalysisRepository analyses;
    private final JevJobRepository jobs;
    private final JevExecutionEventRepository events;
    private final CaseAccessService access;
    private final JevAccessAuditService audit;
    public JevHistoryService(JevAnalysisRepository analyses, JevJobRepository jobs,
            JevExecutionEventRepository events, CaseAccessService access, JevAccessAuditService audit) {
        this.analyses=analyses; this.jobs=jobs; this.events=events; this.access=access; this.audit=audit;
    }
    public record EventView(UUID eventId, UUID requestId, UUID jobId, UUID analysisId,
                            JevExecutionEvent.Type type, String errorCode, Instant occurredAt) {}
    public List<EventView> history(UUID actor, UUID id, UUID requestId, boolean byJob) {
        UUID caseId=null, analysisId=byJob?null:id, jobId=byJob?id:null;
        try {
            if (byJob) {
                var job=jobs.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
                caseId=job.getCaseId(); analysisId=job.getAnalysisId();
            } else {
                var analysis=analyses.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
                caseId=analysis.getCaseId(); jobId=analysis.getJobId();
            }
            access.requireParticipant(caseId,actor);
        } catch (BusinessException e) {
            audit.recordJob(requestId,actor,caseId,analysisId,jobId,JevAccessAudit.Action.READ,
                JevAccessAudit.Outcome.DENIED,e.getErrorCode().name());
            throw e;
        }
        audit.recordJob(requestId,actor,caseId,analysisId,jobId,JevAccessAudit.Action.READ,
            JevAccessAudit.Outcome.ALLOWED,"EXECUTION_HISTORY_READ");
        // Prefer the job timeline when linked; request_id alone is not an authorization/idempotency boundary.
        var rows=jobId!=null?events.findTop100ByJobIdOrderByCreatedAtAscIdAsc(jobId)
            :events.findTop100ByAnalysisIdOrderByCreatedAtAscIdAsc(analysisId);
        return rows.stream().map(e -> new EventView(e.getId(),e.getRequestId(),e.getJobId(),e.getAnalysisId(),
            e.getType(),e.getErrorCode(),e.getCreatedAt())).toList();
    }
}
