package com.insuranceai.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.ai.domain.*;
import com.insuranceai.common.error.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JevResultStore {
    private final JevAnalysisRepository analyses;
    private final JevExecutionEventRepository events;
    private final ObjectMapper mapper;
    public JevResultStore(JevAnalysisRepository analyses, JevExecutionEventRepository events, ObjectMapper mapper) {
        this.analyses=analyses; this.events=events; this.mapper=mapper;
    }
    /** Joins job completion, or owns a short synchronous save transaction. */
    @Transactional
    public JevAnalysis save(UUID caseId, UUID actor, UUID requestId, UUID jobId, JevResult result) {
        if (result == null || !requestId.equals(result.requestId())) throw new BusinessException(ErrorCode.AI_INVALID_RESPONSE);
        try {
            var source=new JevAnalysis.Provenance(requestId,jobId,result.provider(),result.model(),result.promptVersion(),
                result.promptSha256(),result.chainVersion(),result.traceId(),result.traceStatus(),result.inputTokens(),result.outputTokens());
            var saved=analyses.save(JevAnalysis.captured(caseId,actor,source,mapper.writeValueAsString(result)));
            events.save(JevExecutionEvent.create(requestId,jobId,saved.getId(),caseId,actor,
                JevExecutionEvent.Type.RESULT_STORED,null));
            return saved;
        } catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
    }
}
