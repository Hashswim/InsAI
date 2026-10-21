package com.insuranceai.ai.domain;

import com.insuranceai.common.domain.BaseEntity;
import com.insuranceai.common.error.ErrorCode;
import jakarta.persistence.*;
import java.util.Arrays;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Append-only application ledger: no findings, document content or exception text. */
@Entity
@Immutable
@Table(name="jev_execution_events")
public class JevExecutionEvent extends BaseEntity {
    public enum Type { ENQUEUED, STARTED, RESULT_STORED, SUCCEEDED, FAILED, UNKNOWN }
    @Column(nullable=false, updatable=false) private UUID requestId;
    @Column(updatable=false) private UUID jobId;
    @Column(updatable=false) private UUID analysisId;
    @Column(nullable=false, updatable=false) private UUID caseId;
    @Column(nullable=false, updatable=false) private UUID actorId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, updatable=false, length=32) private Type type;
    @Column(updatable=false, length=64) private String errorCode;
    protected JevExecutionEvent() {}
    public static JevExecutionEvent create(UUID requestId, UUID jobId, UUID analysisId, UUID caseId,
                                            UUID actorId, Type type, String code) {
        if (code != null && !code.equals("EXECUTION_UNCERTAIN")
            && Arrays.stream(ErrorCode.values()).noneMatch(e -> e.name().equals(code)))
            throw new IllegalArgumentException("Invalid event code");
        var event=new JevExecutionEvent(); event.assignId();
        event.requestId=requestId; event.jobId=jobId; event.analysisId=analysisId;
        event.caseId=caseId; event.actorId=actorId; event.type=type; event.errorCode=code;
        return event;
    }
    public UUID getRequestId() { return requestId; }
    public UUID getJobId() { return jobId; }
    public UUID getAnalysisId() { return analysisId; }
    public UUID getCaseId() { return caseId; }
    public Type getType() { return type; }
    public String getErrorCode() { return errorCode; }
    @Override public String toString() { return "JevExecutionEvent[REDACTED]"; }
}
