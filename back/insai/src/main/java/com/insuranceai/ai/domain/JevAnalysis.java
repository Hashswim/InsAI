package com.insuranceai.ai.domain;

import com.insuranceai.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name="jev_analyses")
public class JevAnalysis extends BaseEntity {
    @Column(nullable=false) private UUID caseId;
    @Column(nullable=false) private UUID requestedBy;
    @Column(nullable=false, columnDefinition="text") private String resultJson;
    private UUID requestId;
    @Column(unique=true) private UUID jobId;
    @Column(length=20) private String provider;
    @Column(length=200) private String model;
    @Column(length=100) private String promptVersion;
    @Column(length=64) private String promptSha256;
    @Column(length=100) private String chainVersion;
    private UUID traceId;
    @Column(length=16) private String traceStatus;
    private Integer inputTokens;
    private Integer outputTokens;

    protected JevAnalysis() {}
    public static JevAnalysis create(UUID caseId, UUID userId, String resultJson) {
        JevAnalysis entity = new JevAnalysis();
        entity.assignId();
        entity.caseId = caseId;
        entity.requestedBy = userId;
        entity.resultJson = resultJson;
        return entity;
    }
    public UUID getCaseId() { return caseId; }
    public String getResultJson() { return resultJson; }
    public record Provenance(UUID requestId, UUID jobId, String provider, String model, String promptVersion,
                             String promptSha256, String chainVersion, UUID traceId, String traceStatus,
                             int inputTokens, int outputTokens) {}
    public static JevAnalysis captured(UUID caseId, UUID userId, Provenance source, String json) {
        var entity=create(caseId,userId,json);
        entity.requestId=source.requestId(); entity.jobId=source.jobId(); entity.provider=source.provider();
        entity.model=source.model(); entity.promptVersion=source.promptVersion(); entity.promptSha256=source.promptSha256();
        entity.chainVersion=source.chainVersion(); entity.traceId=source.traceId(); entity.traceStatus=source.traceStatus();
        entity.inputTokens=source.inputTokens(); entity.outputTokens=source.outputTokens();
        return entity;
    }
    public UUID getRequestId() { return requestId; }
    public UUID getJobId() { return jobId; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getPromptVersion() { return promptVersion; }
    public String getPromptSha256() { return promptSha256; }
    public String getChainVersion() { return chainVersion; }
    public UUID getTraceId() { return traceId; }
    public String getTraceStatus() { return traceStatus; }
    @Override public String toString() { return "JevAnalysis[REDACTED]"; }
}
