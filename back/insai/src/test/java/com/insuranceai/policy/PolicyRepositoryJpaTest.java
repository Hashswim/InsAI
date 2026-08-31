package com.insuranceai.policy;

import com.insuranceai.policy.domain.Policy;
import com.insuranceai.policy.domain.PolicyPlanType;
import com.insuranceai.policy.domain.PolicyRepository;
import com.insuranceai.policy.domain.PolicyStatus;
import com.insuranceai.support.AbstractJpaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyRepositoryJpaTest extends AbstractJpaTest {

	@Autowired PolicyRepository policies;

	private final UUID owner = UUID.randomUUID();

	private Policy p(String number, LocalDate contract, UUID ownerId) {
		return Policy.register(ownerId, "한화생명", "종신보험", number,
				PolicyPlanType.WHOLE_LIFE, "홍길동", contract, contract, null,
				100_000_000L, 100_000L, "사망 1억", PolicyStatus.ACTIVE);
	}

	@Test
	@DisplayName("findByOwnerIdOrderByContractDateDesc: 소유자 것만, 계약일 내림차순")
	void byOwner() {
		policies.save(p("A", LocalDate.of(2015, 1, 1), owner));
		policies.save(p("B", LocalDate.of(2021, 6, 1), owner));
		policies.save(p("C", LocalDate.of(2019, 3, 1), UUID.randomUUID()));

		assertThat(policies.findByOwnerIdOrderByContractDateDesc(owner))
				.extracting(Policy::getPolicyNumber).containsExactly("B", "A");
	}

	@Test
	@DisplayName("existsByOwnerId")
	void exists() {
		assertThat(policies.existsByOwnerId(owner)).isFalse();
		policies.save(p("A", LocalDate.of(2015, 1, 1), owner));
		assertThat(policies.existsByOwnerId(owner)).isTrue();
	}
}
