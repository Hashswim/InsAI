package com.insuranceai.ai.domain;

import com.insuranceai.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="jev_analyses")
public class JevAnalysis extends BaseEntity {
    @Column(nullable=false) private UUID caseId;
    @Column(nullable=false) private UUID requestedBy;
    @Column(nullable=false, columnDefinition="text") private String resultJson;

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
}
