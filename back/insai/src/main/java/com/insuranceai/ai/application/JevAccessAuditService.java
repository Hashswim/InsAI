package com.insuranceai.ai.application;

import com.insuranceai.ai.domain.JevAccessAudit;
import com.insuranceai.ai.domain.JevAccessAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class JevAccessAuditService {
    private final JevAccessAuditRepository audits;
    public JevAccessAuditService(JevAccessAuditRepository audits) { this.audits=audits; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID requestId, UUID actorId, UUID caseId, UUID analysisId,
                       JevAccessAudit.Action action, JevAccessAudit.Outcome outcome, String reasonCode) {
        audits.save(JevAccessAudit.create(requestId, actorId, caseId, analysisId, action, outcome, reasonCode));
    }
}
