package com.insuranceai.policy.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

	List<Policy> findByOwnerIdOrderByContractDateDesc(UUID ownerId);

	boolean existsByOwnerId(UUID ownerId);
}
