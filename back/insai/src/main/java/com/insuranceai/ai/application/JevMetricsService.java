package com.insuranceai.ai.application;

import com.insuranceai.ai.domain.*;
import com.insuranceai.collab.application.CaseAccessService;
import com.insuranceai.common.error.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JevMetricsService {
    private final JevAnalysisRepository analyses;
    private final JevJobRepository jobs;
    private final CaseAccessService access;
    private final JevAccessAuditService audit;
    public JevMetricsService(JevAnalysisRepository analyses, JevJobRepository jobs, CaseAccessService access, JevAccessAuditService audit) {
        this.analyses=analyses; this.jobs=jobs; this.access=access; this.audit=audit;
    }
    public record Metrics(Instant since, Instant until, long storedAnalyses, long inputTokens,
                          long outputTokens, long tokenMetadataRows, Map<String,Long> traceStatuses, Map<String,Long> jobStatuses) {}
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Metrics get(UUID user, UUID caseId, UUID requestId, int days) {
        if (days<1 || days>31) throw new BusinessException(ErrorCode.INVALID_INPUT);
        try { access.requireParticipant(caseId,user); }
        catch (BusinessException e) {
            audit.record(requestId,user,caseId,null,JevAccessAudit.Action.READ,JevAccessAudit.Outcome.DENIED,e.getErrorCode().name());
            throw e;
        }
        audit.record(requestId,user,caseId,null,JevAccessAudit.Action.READ,JevAccessAudit.Outcome.ALLOWED,"METRICS_READ");
        var until=Instant.now(); var since=until.minus(days,ChronoUnit.DAYS);
        var totals=analyses.totals(caseId,since,until);
        Map<String,Long> traces=new LinkedHashMap<>();
        for (String label : List.of("RECORDED","FAILED","DISABLED","UNKNOWN")) traces.put(label,0L);
        for (var row : analyses.traces(caseId,since,until)) {
            String label=traces.containsKey(row.getTraceStatus())?row.getTraceStatus():"UNKNOWN";
            traces.merge(label,row.getTotal(),Long::sum);
        }
        Map<String,Long> statuses=new LinkedHashMap<>();
        for (var status : JevJob.Status.values()) statuses.put(status.name(),0L);
        for (var row : jobs.statusCounts(caseId,since,until)) statuses.put(row.getStatus().name(),row.getTotal());
        return new Metrics(since,until,totals.getStored(),totals.getInputTokens()==null?0:totals.getInputTokens(),
            totals.getOutputTokens()==null?0:totals.getOutputTokens(),totals.getUsageRows(),traces,statuses);
    }
}
