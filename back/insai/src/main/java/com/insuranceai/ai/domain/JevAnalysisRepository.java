package com.insuranceai.ai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface JevAnalysisRepository extends JpaRepository<JevAnalysis, UUID> {
    interface Totals { long getStored(); Long getInputTokens(); Long getOutputTokens(); long getUsageRows(); }
    @org.springframework.data.jpa.repository.Query("select count(a) as stored, sum(a.inputTokens) as inputTokens, sum(a.outputTokens) as outputTokens, count(a.inputTokens) as usageRows from JevAnalysis a where a.caseId=:caseId and a.createdAt>=:since and a.createdAt<:until")
    Totals totals(@org.springframework.data.repository.query.Param("caseId") UUID caseId,
        @org.springframework.data.repository.query.Param("since") java.time.Instant since,
        @org.springframework.data.repository.query.Param("until") java.time.Instant until);
    interface TraceCount { String getTraceStatus(); long getTotal(); }
    @org.springframework.data.jpa.repository.Query("select a.traceStatus as traceStatus, count(a) as total from JevAnalysis a where a.caseId=:caseId and a.createdAt>=:since and a.createdAt<:until group by a.traceStatus")
    java.util.List<TraceCount> traces(@org.springframework.data.repository.query.Param("caseId") UUID caseId,
        @org.springframework.data.repository.query.Param("since") java.time.Instant since,
        @org.springframework.data.repository.query.Param("until") java.time.Instant until);
    interface ProviderCount { String getProvider(); long getTotal(); }
    @org.springframework.data.jpa.repository.Query("select a.provider as provider, count(a) as total from JevAnalysis a where a.caseId=:caseId and a.createdAt>=:since and a.createdAt<:until group by a.provider")
    java.util.List<ProviderCount> providers(@org.springframework.data.repository.query.Param("caseId") UUID caseId,
        @org.springframework.data.repository.query.Param("since") java.time.Instant since,
        @org.springframework.data.repository.query.Param("until") java.time.Instant until);
}
