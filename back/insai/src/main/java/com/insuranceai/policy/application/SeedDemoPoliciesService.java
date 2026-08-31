package com.insuranceai.policy.application;

import com.insuranceai.policy.domain.Policy;
import com.insuranceai.policy.domain.PolicyPlanType;
import com.insuranceai.policy.domain.PolicyRepository;
import com.insuranceai.policy.domain.PolicyStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 정식 보험사 연동 전, 데모용 생명보험 계약을 청구인에게 심는다.
 * 이미 계약이 있으면 아무것도 하지 않는다. 증권번호는 ownerId 에서 파생해 데모 사용자 간 충돌을 피한다.
 */
@Service
public class SeedDemoPoliciesService {

	private final PolicyRepository policies;

	public SeedDemoPoliciesService(PolicyRepository policies) {
		this.policies = policies;
	}

	@Transactional
	public void seedFor(UUID ownerId, String insuredName) {
		if (ownerId == null || policies.existsByOwnerId(ownerId)) {
			return;
		}
		String suffix = ownerId.toString().replace("-", "").substring(0, 8).toUpperCase();
		String name = (insuredName == null || insuredName.isBlank()) ? "홍길동" : insuredName.trim();

		policies.saveAll(List.of(
				Policy.register(ownerId, "한화생명", "평생든든 종신보험", "HL-" + suffix + "-01",
						PolicyPlanType.WHOLE_LIFE, name,
						LocalDate.of(2016, 3, 10), LocalDate.of(2016, 3, 10), null,
						100_000_000L, 182_000L,
						"일반사망 1억 / 재해사망 2억 / 질병입원일당 3만원", PolicyStatus.ACTIVE),
				Policy.register(ownerId, "삼성생명", "행복한 암보장보험", "SL-" + suffix + "-02",
						PolicyPlanType.CANCER, name,
						LocalDate.of(2019, 7, 1), LocalDate.of(2019, 7, 1), LocalDate.of(2049, 7, 1),
						30_000_000L, 47_500L,
						"암진단비 3천만원 / 소액암 600만원 / 항암치료비 연 500만원", PolicyStatus.ACTIVE),
				Policy.register(ownerId, "교보생명", "정기보험 라이트", "KL-" + suffix + "-03",
						PolicyPlanType.TERM_LIFE, name,
						LocalDate.of(2014, 1, 20), LocalDate.of(2014, 1, 20), LocalDate.of(2024, 1, 20),
						50_000_000L, 21_000L,
						"사망보험금 5천만원 (10년 만기)", PolicyStatus.EXPIRED)
		));
	}
}
