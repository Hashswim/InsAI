package com.insuranceai.ai.domain;

import com.insuranceai.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "jev_access_audits")
public class JevAccessAudit extends BaseEntity {
    public enum Action { ANALYZE, READ }
    public enum Outcome { ALLOWED, DENIED }
    @Column(nullable = false) private UUID requestId;
    @Column(nullable = false) private UUID actorId;
    private UUID caseId;
    private UUID analysisId;
    private UUID jobId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Action action;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Outcome outcome;
    @Column(nullable = false, length = 64) private String reasonCode;
    protected JevAccessAudit() {}
    public static JevAccessAudit create(UUID requestId, UUID actorId, UUID caseId, UUID analysisId,
                                       Action action, Outcome outcome, String reasonCode) {
        var audit = new JevAccessAudit();
        audit.assignId();
        audit.requestId=requestId; audit.actorId=actorId; audit.caseId=caseId; audit.analysisId=analysisId;
        audit.action=action; audit.outcome=outcome; audit.reasonCode=reasonCode;
        return audit;
    }
    public UUID getRequestId() { return requestId; }
    public static JevAccessAudit forJob(UUID requestId, UUID actorId, UUID caseId, UUID analysisId, UUID jobId,
                                       Action action, Outcome outcome, String reasonCode) {
        var audit=create(requestId,actorId,caseId,analysisId,action,outcome,reasonCode);
        audit.jobId=jobId;
        return audit;
    }
    public UUID getJobId() { return jobId; }
    public UUID getActorId() { return actorId; }
    public UUID getCaseId() { return caseId; }
    public Outcome getOutcome() { return outcome; }
    public Action getAction() { return action; }
    public String getReasonCode() { return reasonCode; }
}
