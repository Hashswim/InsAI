package com.insuranceai.collab.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, UUID> {

	List<TimelineEvent> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
