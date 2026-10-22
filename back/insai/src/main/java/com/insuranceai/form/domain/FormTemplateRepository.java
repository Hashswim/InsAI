package com.insuranceai.form.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FormTemplateRepository extends JpaRepository<FormTemplate, UUID> {

	Optional<FormTemplate> findTopByCodeOrderByVersionDesc(String code);

	List<FormTemplate> findByCodeOrderByVersionDesc(String code);
}
