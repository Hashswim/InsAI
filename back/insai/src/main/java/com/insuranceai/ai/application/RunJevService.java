package com.insuranceai.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.ai.domain.*;
import com.insuranceai.collab.application.CaseAccessService;
import com.insuranceai.document.application.ReadClaimPdfService;
import com.insuranceai.common.error.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class RunJevService {
    private final CaseAccessService access;
    private final ReadClaimPdfService files;
    private final JevGateway gateway;
    private final JevAnalysisRepository analyses;
    private final ObjectMapper mapper;
    private final JevAccessAuditService audit;
    private final JevResultStore resultStore;
    private final JevExecutionAuditService executionAudit;

    public RunJevService(CaseAccessService access, ReadClaimPdfService files, JevGateway gateway,
                         JevAnalysisRepository analyses, ObjectMapper mapper, JevAccessAuditService audit,
                         JevResultStore resultStore, JevExecutionAuditService executionAudit) {
        this.access=access; this.files=files; this.gateway=gateway; this.analyses=analyses; this.mapper=mapper;
        this.audit=audit;
        this.resultStore=resultStore; this.executionAudit=executionAudit;
    }
    public record AnalysisView(UUID analysisId, JevResult result) {
        public AnalysisView forClaimant() { return new AnalysisView(analysisId, result.forClaimant()); }
    }
    public AnalysisView run(UUID userId, RunJevCommand command) {
        return run(userId, command, UUID.randomUUID());
    }
    public AnalysisView run(UUID userId, RunJevCommand command, UUID requestId) {
        try {
            var result = analyze(userId, command, requestId);
            var saved = resultStore.save(command.caseId(),userId,requestId,null,result);
            return new AnalysisView(saved.getId(), result);
        } catch (BusinessException e) {
            executionAudit.synchronousFailure(requestId,userId,command.caseId(),e.getErrorCode());
            throw e;
        } catch (RuntimeException e) {
            executionAudit.synchronousFailure(requestId,userId,command.caseId(),ErrorCode.INTERNAL_ERROR);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /** Worker entry point: does not persist; job completion owns the atomic result transaction. */
    public JevResult analyze(UUID userId, RunJevCommand command, UUID requestId) {
        List<JevGateway.Document> documents;
        try {
            access.requireParticipant(command.caseId(), userId);
            var refs = command.documents();
            if (refs == null || refs.size()!=3
                || refs.stream().anyMatch(ref -> ref == null || ref.fileId() == null || ref.kind() == null)
                || refs.stream().map(RunJevCommand.FileRef::fileId).distinct().count()!=3
                || !RunJevCommand.validKinds(refs))
                throw new BusinessException(ErrorCode.INVALID_INPUT, "각 종류별 PDF 한 개씩 필요합니다.");
            documents = refs.stream().map(ref -> new JevGateway.Document(
                ref.fileId(), ref.kind().name(),
                Base64.getEncoder().encodeToString(files.read(command.caseId(), userId, ref.fileId()))
            )).toList();
        } catch (BusinessException e) {
            audit.record(requestId, userId, command.caseId(), null, JevAccessAudit.Action.ANALYZE,
                JevAccessAudit.Outcome.DENIED, e.getErrorCode().name());
            throw e;
        }
        audit.record(requestId, userId, command.caseId(), null, JevAccessAudit.Action.ANALYZE,
            JevAccessAudit.Outcome.ALLOWED, "AUTHORIZED");
        return gateway.analyze(new JevGateway.Request(requestId, documents));
    }
    /** The case's most recent analysis (whoever ran it), or null when there is none yet. */
    public AnalysisView latest(UUID userId, UUID caseId, UUID requestId) {
        try {
            access.requireParticipant(caseId, userId);
        } catch (BusinessException e) {
            audit.record(requestId, userId, caseId, null, JevAccessAudit.Action.READ,
                JevAccessAudit.Outcome.DENIED, e.getErrorCode().name());
            throw e;
        }
        var analysis = analyses.findFirstByCaseIdOrderByCreatedAtDesc(caseId).orElse(null);
        audit.record(requestId, userId, caseId, analysis == null ? null : analysis.getId(),
            JevAccessAudit.Action.READ, JevAccessAudit.Outcome.ALLOWED, "LATEST_READ");
        if (analysis == null) return null;
        try {
            return new AnalysisView(analysis.getId(), mapper.readValue(analysis.getResultJson(), JevResult.class));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
    public AnalysisView get(UUID userId, UUID analysisId) {
        return get(userId, analysisId, UUID.randomUUID());
    }
    public AnalysisView get(UUID userId, UUID analysisId, UUID requestId) {
        JevAnalysis analysis = null;
        try {
            analysis = analyses.findById(analysisId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            access.requireParticipant(analysis.getCaseId(), userId);
        } catch (BusinessException e) {
            audit.recordJob(requestId, userId, analysis == null ? null : analysis.getCaseId(), analysisId,
                analysis == null ? null : analysis.getJobId(),
                JevAccessAudit.Action.READ, JevAccessAudit.Outcome.DENIED, e.getErrorCode().name());
            throw e;
        }
        audit.recordJob(requestId, userId, analysis.getCaseId(), analysisId, analysis.getJobId(),
            JevAccessAudit.Action.READ, JevAccessAudit.Outcome.ALLOWED, "AUTHORIZED");
        try {
            return new AnalysisView(analysisId, mapper.readValue(analysis.getResultJson(), JevResult.class));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
