package com.insuranceai.collab.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

	Optional<Decision> findByCaseId(UUID caseId);

	List<Decision> findAllByCaseId(UUID caseId);
}
