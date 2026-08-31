package com.insuranceai.policy.domain;

import com.insuranceai.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 청구인이 가입한 보험 계약(증권) 한 건.
 */
@Entity
@Table(name = "policies",
		uniqueConstraints = @UniqueConstraint(name = "uk_policies_policy_number", columnNames = "policy_number"))
public class Policy extends BaseEntity {

	@Column(nullable = false)
	private UUID ownerId;

	@Column(nullable = false, length = 60)
	private String insurerName;

	@Column(nullable = false, length = 120)
	private String productName;

	@Column(name = "policy_number", nullable = false, length = 40)
	private String policyNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PolicyPlanType planType;

	@Column(nullable = false, length = 40)
	private String insuredName;

	@Column(nullable = false)
	private LocalDate contractDate;

	@Column(nullable = false)
	private LocalDate coverageStartDate;

	@Column
	private LocalDate coverageEndDate;

	@Column(nullable = false)
	private long coverageAmount;

	@Column(nullable = false)
	private long monthlyPremium;

	@Column(length = 500)
	private String coverageSummary;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PolicyStatus status;

	protected Policy() {
	}

	public static Policy register(UUID ownerId, String insurerName, String productName, String policyNumber,
	                              PolicyPlanType planType, String insuredName,
	                              LocalDate contractDate, LocalDate coverageStartDate, LocalDate coverageEndDate,
	                              long coverageAmount, long monthlyPremium, String coverageSummary,
	                              PolicyStatus status) {
		Policy p = new Policy();
		p.assignId();
		p.ownerId = Objects.requireNonNull(ownerId, "ownerId");
		p.insurerName = requireText(insurerName, "보험사");
		p.productName = requireText(productName, "상품명");
		p.policyNumber = requireText(policyNumber, "증권번호");
		p.planType = Objects.requireNonNull(planType, "planType");
		p.insuredName = requireText(insuredName, "피보험자");
		p.contractDate = Objects.requireNonNull(contractDate, "contractDate");
		p.coverageStartDate = Objects.requireNonNull(coverageStartDate, "coverageStartDate");
		p.coverageEndDate = coverageEndDate;
		p.coverageAmount = coverageAmount;
		p.monthlyPremium = monthlyPremium;
		p.coverageSummary = coverageSummary;
		p.status = Objects.requireNonNull(status, "status");
		return p;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public String getInsurerName() {
		return insurerName;
	}

	public String getProductName() {
		return productName;
	}

	public String getPolicyNumber() {
		return policyNumber;
	}

	public PolicyPlanType getPlanType() {
		return planType;
	}

	public String getInsuredName() {
		return insuredName;
	}

	public LocalDate getContractDate() {
		return contractDate;
	}

	public LocalDate getCoverageStartDate() {
		return coverageStartDate;
	}

	public LocalDate getCoverageEndDate() {
		return coverageEndDate;
	}

	public long getCoverageAmount() {
		return coverageAmount;
	}

	public long getMonthlyPremium() {
		return monthlyPremium;
	}

	public String getCoverageSummary() {
		return coverageSummary;
	}

	public PolicyStatus getStatus() {
		return status;
	}
}
