package com.insuranceai.ai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface JevAnalysisRepository extends JpaRepository<JevAnalysis, UUID> {}
