package com.insuranceai.policy.application;

import com.insuranceai.policy.domain.Policy;

import java.time.LocalDate;
import java.util.UUID;

public record PolicyView(
		UUID id,
		String insurerName,
		String productName,
		String policyNumber,
		String planType,
		String planTypeLabel,
		String insuredName,
		LocalDate contractDate,
		LocalDate coverageStartDate,
		LocalDate coverageEndDate,
		long coverageAmount,
		long monthlyPremium,
		String coverageSummary,
		String status,
		String statusLabel
) {

	public static PolicyView from(Policy p) {
		return new PolicyView(
				p.getId(),
				p.getInsurerName(),
				p.getProductName(),
				p.getPolicyNumber(),
				p.getPlanType().name(),
				p.getPlanType().label(),
				p.getInsuredName(),
				p.getContractDate(),
				p.getCoverageStartDate(),
				p.getCoverageEndDate(),
				p.getCoverageAmount(),
				p.getMonthlyPremium(),
				p.getCoverageSummary(),
				p.getStatus().name(),
				p.getStatus().label());
	}
}
