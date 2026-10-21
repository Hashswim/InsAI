package com.insuranceai.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.ai.domain.*;
import com.insuranceai.collab.application.CaseAccessService;
import com.insuranceai.common.error.*;
import com.insuranceai.document.application.ReadClaimPdfService;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class JevJobService {
    private final JevJobRepository jobs;
    private final JevJobStore store;
    private final CaseAccessService access;
    private final ReadClaimPdfService files;
    private final ObjectMapper mapper;
    private final JevAccessAuditService audit;
    public JevJobService(JevJobRepository jobs, JevJobStore store, CaseAccessService access,
                         ReadClaimPdfService files, ObjectMapper mapper, JevAccessAuditService audit) {
        this.jobs=jobs; this.store=store; this.access=access; this.files=files; this.mapper=mapper;
        this.audit=audit;
    }
    public record JobView(UUID jobId, UUID requestId, JevJob.Status status, UUID analysisId,
                          String errorCode, Instant createdAt, Instant startedAt, Instant finishedAt) {}

    public JobView submit(UUID user, UUID key, UUID requestId, RunJevCommand command) {
        if (key == null || requestId == null || command == null || command.caseId() == null)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        try { return submitAuthorized(user,key,requestId,command); }
        catch (BusinessException e) {
            audit.record(requestId,user,command.caseId(),null,JevAccessAudit.Action.ANALYZE,
                JevAccessAudit.Outcome.DENIED,e.getErrorCode().name());
            throw e;
        }
    }
    private JobView submitAuthorized(UUID user, UUID key, UUID requestId, RunJevCommand command) {
        access.requireParticipant(command.caseId(),user);
        var refs = command.documents();
        if (refs == null || refs.size()!=3 || refs.stream().anyMatch(r -> r == null || r.fileId()==null || r.kind()==null)
            || refs.stream().map(RunJevCommand.FileRef::fileId).distinct().count()!=3
            || refs.stream().map(RunJevCommand.FileRef::kind).distinct().count()!=3)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        String json;
        try {
            json=mapper.writeValueAsString(new RunJevCommand(command.caseId(),refs.stream()
                .sorted(Comparator.comparing(r -> r.kind().name())).toList()));
        } catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
        var existing=jobs.findByRequestedByAndIdempotencyKey(user,key);
        if (existing.isPresent()) {
            var view=same(existing.get(),json);
            audit.recordJob(requestId,user,command.caseId(),existing.get().getAnalysisId(),existing.get().getId(),JevAccessAudit.Action.ANALYZE,
                JevAccessAudit.Outcome.ALLOWED,"JOB_REUSED");
            return view;
        }
        // Validate file ownership and PDF basics now; the worker rechecks before execution.
        for (var ref : refs) files.read(command.caseId(),user,ref.fileId());
        audit.record(requestId,user,command.caseId(),null,JevAccessAudit.Action.ANALYZE,
            JevAccessAudit.Outcome.ALLOWED,"JOB_SUBMIT_AUTHORIZED");
        try { return view(store.enqueue(user,key,requestId,command.caseId(),json)); }
        catch (DataIntegrityViolationException e) {
            var raced=jobs.findByRequestedByAndIdempotencyKey(user,key);
            if (raced.isPresent()) return same(raced.get(),json);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
    public JobView get(UUID user, UUID id) {
        return get(user,id,UUID.randomUUID());
    }
    public JobView get(UUID user, UUID id, UUID requestId) {
        JevJob job=null;
        try {
            job=jobs.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            access.requireParticipant(job.getCaseId(),user);
        } catch (BusinessException e) {
            audit.recordJob(requestId,user,job==null?null:job.getCaseId(),null,id,JevAccessAudit.Action.READ,
                JevAccessAudit.Outcome.DENIED,e.getErrorCode().name());
            throw e;
        }
        audit.recordJob(requestId,user,job.getCaseId(),job.getAnalysisId(),job.getId(),JevAccessAudit.Action.READ,
            JevAccessAudit.Outcome.ALLOWED,"JOB_READ_AUTHORIZED");
        return view(job);
    }
    private JobView same(JevJob job, String json) {
        if (!job.getCommandJson().equals(json)) throw new BusinessException(ErrorCode.CONFLICT);
        return view(job);
    }
    private JobView view(JevJob job) {
        return new JobView(job.getId(),job.getRequestId(),job.getStatus(),job.getAnalysisId(),job.getErrorCode(),
            job.getCreatedAt(),job.getStartedAt(),job.getFinishedAt());
    }
}
