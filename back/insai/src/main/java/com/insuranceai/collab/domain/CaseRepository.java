package com.insuranceai.collab.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, UUID> {

	List<Case> findByClaimantId(UUID claimantId);

	List<Case> findByAdjusterId(UUID adjusterId);

	List<Case> findByStatus(CaseStatus status);
}
