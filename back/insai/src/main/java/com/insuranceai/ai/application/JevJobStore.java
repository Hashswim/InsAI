package com.insuranceai.ai.application;

import com.insuranceai.ai.domain.*;
import com.insuranceai.common.error.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Only short database transactions; never holds a transaction across a model call. */
@Service
public class JevJobStore {
    private final JevJobRepository jobs;
    private final JevResultStore results;
    private final JevExecutionEventRepository events;
    private final JevAccessAuditRepository accessAudits;
    public JevJobStore(JevJobRepository jobs, JevResultStore results, JevExecutionEventRepository events,
                       JevAccessAuditRepository accessAudits) {
        this.jobs=jobs; this.results=results; this.events=events;
        this.accessAudits=accessAudits;
    }
    @Transactional
    public JevJob enqueue(UUID user, UUID key, UUID requestId, UUID caseId, String command) {
        var job=jobs.saveAndFlush(JevJob.queued(caseId,user,key,requestId,command));
        event(job,JevExecutionEvent.Type.ENQUEUED,null);
        accessAudits.save(JevAccessAudit.forJob(requestId,user,caseId,null,job.getId(),JevAccessAudit.Action.ANALYZE,
            JevAccessAudit.Outcome.ALLOWED,"JOB_ENQUEUED"));
        return job;
    }
    @Transactional
    public Optional<JevJob> claim() {
        for (var job : jobs.findTop10ByStatusOrderByCreatedAtAsc(JevJob.Status.QUEUED)) {
            if (jobs.claim(job.getId(),Instant.now()) == 1) {
                var claimed=jobs.findById(job.getId()).orElseThrow();
                event(claimed,JevExecutionEvent.Type.STARTED,null);
                return Optional.of(claimed);
            }
        }
        return Optional.empty();
    }
    @Transactional
    public void complete(UUID id, JevResult result) {
        var job = jobs.lockById(id).orElseThrow();
        if (job.getStatus() != JevJob.Status.RUNNING) return;
        var analysis=results.save(job.getCaseId(),job.getRequestedBy(),job.getRequestId(),job.getId(),result);
        job.succeed(analysis.getId(),Instant.now());
        event(job,JevExecutionEvent.Type.SUCCEEDED,null);
    }
    @Transactional
    public void fail(UUID id, ErrorCode code) {
        var job = jobs.lockById(id).orElseThrow();
        if (job.getStatus() == JevJob.Status.RUNNING) {
            job.fail(code.name(),Instant.now());
            event(job,JevExecutionEvent.Type.FAILED,code.name());
        }
    }
    @Transactional
    public int expire(Instant now) {
        int count=0;
        for (var candidate : jobs.findExpiredIds(now.minusSeconds(900),org.springframework.data.domain.PageRequest.of(0,100))) {
            var job=jobs.lockById(candidate).orElseThrow();
            if (job.getStatus()==JevJob.Status.RUNNING && job.getStartedAt().isBefore(now.minusSeconds(900))) {
                job.uncertain(now);
                event(job,JevExecutionEvent.Type.UNKNOWN,"EXECUTION_UNCERTAIN");
                count++;
            }
        }
        return count;
    }
    private void event(JevJob job, JevExecutionEvent.Type type, String code) {
        events.save(JevExecutionEvent.create(job.getRequestId(),job.getId(),job.getAnalysisId(),job.getCaseId(),
            job.getRequestedBy(),type,code));
    }
}
