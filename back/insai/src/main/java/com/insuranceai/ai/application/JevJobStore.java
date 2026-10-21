package com.insuranceai.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final JevAnalysisRepository analyses;
    private final ObjectMapper mapper;
    public JevJobStore(JevJobRepository jobs, JevAnalysisRepository analyses, ObjectMapper mapper) {
        this.jobs=jobs; this.analyses=analyses; this.mapper=mapper;
    }
    @Transactional
    public JevJob enqueue(UUID user, UUID key, UUID requestId, UUID caseId, String command) {
        return jobs.saveAndFlush(JevJob.queued(caseId,user,key,requestId,command));
    }
    @Transactional
    public Optional<JevJob> claim() {
        for (var job : jobs.findTop10ByStatusOrderByCreatedAtAsc(JevJob.Status.QUEUED)) {
            if (jobs.claim(job.getId(),Instant.now()) == 1) return jobs.findById(job.getId());
        }
        return Optional.empty();
    }
    @Transactional
    public void complete(UUID id, JevResult result) {
        var job = jobs.lockById(id).orElseThrow();
        if (job.getStatus() != JevJob.Status.RUNNING) return;
        try {
            var analysis = analyses.save(JevAnalysis.create(job.getCaseId(),job.getRequestedBy(),mapper.writeValueAsString(result)));
            job.succeed(analysis.getId(),Instant.now());
        } catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
    }
    @Transactional
    public void fail(UUID id, ErrorCode code) {
        var job = jobs.lockById(id).orElseThrow();
        if (job.getStatus() == JevJob.Status.RUNNING) job.fail(code.name(),Instant.now());
    }
    @Transactional
    public int expire(Instant now) {
        return jobs.expire(now.minusSeconds(900),now);
    }
}
