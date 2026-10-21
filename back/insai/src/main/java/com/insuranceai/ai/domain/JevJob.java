package com.insuranceai.ai.domain;

import com.insuranceai.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jev_jobs", uniqueConstraints = @UniqueConstraint(columnNames = {"requested_by", "idempotency_key"}))
public class JevJob extends BaseEntity {
    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED, UNKNOWN }
    @Column(nullable=false) private UUID caseId;
    @Column(nullable=false) private UUID requestedBy;
    @Column(nullable=false) private UUID idempotencyKey;
    @Column(nullable=false) private UUID requestId;
    @Column(nullable=false, columnDefinition="text") private String commandJson;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private Status status;
    private Instant startedAt;
    private Instant finishedAt;
    private UUID analysisId;
    @Column(length=64) private String errorCode;

    protected JevJob() {}
    public static JevJob queued(UUID caseId, UUID user, UUID key, UUID requestId, String command) {
        var job = new JevJob();
        job.assignId(); job.caseId=caseId; job.requestedBy=user; job.idempotencyKey=key;
        job.requestId=requestId; job.commandJson=command; job.status=Status.QUEUED;
        return job;
    }
    public void succeed(UUID result, Instant now) {
        if (status != Status.RUNNING) throw new IllegalStateException("Job is not running");
        status=Status.SUCCEEDED; analysisId=result; finishedAt=now;
    }
    public void fail(String code, Instant now) {
        if (status != Status.RUNNING) throw new IllegalStateException("Job is not running");
        status=Status.FAILED; errorCode=code; finishedAt=now;
    }
    public UUID getCaseId() { return caseId; }
    public UUID getRequestedBy() { return requestedBy; }
    public UUID getRequestId() { return requestId; }
    public String getCommandJson() { return commandJson; }
    public Status getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public UUID getAnalysisId() { return analysisId; }
    public String getErrorCode() { return errorCode; }
    @Override public String toString() { return "JevJob[REDACTED]"; }
}
