package com.insuranceai.ai.application;

import com.insuranceai.ai.domain.*;
import com.insuranceai.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JevExecutionAuditService {
    private final JevExecutionEventRepository events;
    public JevExecutionAuditService(JevExecutionEventRepository events) { this.events=events; }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void synchronousFailure(UUID requestId, UUID actor, UUID caseId, ErrorCode code) {
        events.save(JevExecutionEvent.create(requestId,null,null,caseId,actor,JevExecutionEvent.Type.FAILED,code.name()));
    }
}
