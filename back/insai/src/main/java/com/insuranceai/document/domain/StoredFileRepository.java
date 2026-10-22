package com.insuranceai.document.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

	List<StoredFile> findByCaseIdOrderByCreatedAtAsc(UUID caseId);

	List<StoredFile> findBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);
}
