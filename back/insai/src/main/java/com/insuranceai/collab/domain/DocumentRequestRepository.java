package com.insuranceai.collab.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRequestRepository extends JpaRepository<DocumentRequest, UUID> {

	List<DocumentRequest> findByCaseId(UUID caseId);

	List<DocumentRequest> findByCaseIdAndStatus(UUID caseId, DocumentRequestStatus status);
}
