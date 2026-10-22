package com.insuranceai.ai.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface JevExecutionEventRepository extends Repository<JevExecutionEvent,UUID> {
    JevExecutionEvent save(JevExecutionEvent event);
    List<JevExecutionEvent> findTop100ByJobIdOrderByCreatedAtAscIdAsc(UUID jobId);
    List<JevExecutionEvent> findTop100ByAnalysisIdOrderByCreatedAtAscIdAsc(UUID analysisId);
    List<JevExecutionEvent> findTop100ByRequestIdOrderByCreatedAtAscIdAsc(UUID requestId);
}
