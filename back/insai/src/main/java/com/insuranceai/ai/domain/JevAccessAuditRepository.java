package com.insuranceai.ai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JevAccessAuditRepository extends JpaRepository<JevAccessAudit, UUID> {
    List<JevAccessAudit> findByRequestId(UUID requestId);
}
