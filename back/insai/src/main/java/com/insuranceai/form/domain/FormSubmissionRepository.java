package com.insuranceai.form.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FormSubmissionRepository extends JpaRepository<FormSubmission, UUID> {

	Optional<FormSubmission> findByDocumentRequestId(UUID documentRequestId);

	List<FormSubmission> findByCaseId(UUID caseId);
}
