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

    public RunJevService(CaseAccessService access, ReadClaimPdfService files, JevGateway gateway,
                         JevAnalysisRepository analyses, ObjectMapper mapper, JevAccessAuditService audit) {
        this.access=access; this.files=files; this.gateway=gateway; this.analyses=analyses; this.mapper=mapper;
        this.audit=audit;
    }
    public record AnalysisView(UUID analysisId, JevResult result) {}
    public AnalysisView run(UUID userId, RunJevCommand command) {
        return run(userId, command, UUID.randomUUID());
    }
    public AnalysisView run(UUID userId, RunJevCommand command, UUID requestId) {
        List<JevGateway.Document> documents;
        try {
            access.requireParticipant(command.caseId(), userId);
            var refs = command.documents();
            if (refs == null || refs.size()!=3
                || refs.stream().anyMatch(ref -> ref == null || ref.fileId() == null || ref.kind() == null)
                || refs.stream().map(RunJevCommand.FileRef::fileId).distinct().count()!=3
                || refs.stream().map(RunJevCommand.FileRef::kind).distinct().count()!=3)
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
        var result = gateway.analyze(new JevGateway.Request(requestId, documents));
        try {
            var saved = analyses.save(JevAnalysis.create(command.caseId(), userId, mapper.writeValueAsString(result)));
            return new AnalysisView(saved.getId(), result);
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
            audit.record(requestId, userId, analysis == null ? null : analysis.getCaseId(), analysisId,
                JevAccessAudit.Action.READ, JevAccessAudit.Outcome.DENIED, e.getErrorCode().name());
            throw e;
        }
        audit.record(requestId, userId, analysis.getCaseId(), analysisId,
            JevAccessAudit.Action.READ, JevAccessAudit.Outcome.ALLOWED, "AUTHORIZED");
        try {
            return new AnalysisView(analysisId, mapper.readValue(analysis.getResultJson(), JevResult.class));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
