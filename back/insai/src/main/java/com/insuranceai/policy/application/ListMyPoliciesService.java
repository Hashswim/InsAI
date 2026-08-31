package com.insuranceai.policy.application;

import com.insuranceai.policy.domain.PolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListMyPoliciesService {

	private final PolicyRepository policies;

	public ListMyPoliciesService(PolicyRepository policies) {
		this.policies = policies;
	}

	@Transactional(readOnly = true)
	public List<PolicyView> list(UUID ownerId) {
		return policies.findByOwnerIdOrderByContractDateDesc(ownerId).stream()
				.map(PolicyView::from)
				.toList();
	}
}
