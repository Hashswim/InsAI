package com.insuranceai.policy.application;

import com.insuranceai.policy.domain.Policy;
import com.insuranceai.policy.domain.PolicyPlanType;
import com.insuranceai.policy.domain.PolicyRepository;
import com.insuranceai.policy.domain.PolicyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyServicesTest {

	@Mock PolicyRepository policies;

	@InjectMocks ListMyPoliciesService listMyPoliciesService;
	@InjectMocks SeedDemoPoliciesService seedDemoPoliciesService;

	@Captor ArgumentCaptor<List<Policy>> saved;

	private final UUID owner = UUID.randomUUID();

	private Policy p(String number, LocalDate contract) {
		return Policy.register(owner, "한화생명", "종신보험", number,
				PolicyPlanType.WHOLE_LIFE, "홍길동", contract, contract, null,
				100_000_000L, 100_000L, "사망 1억", PolicyStatus.ACTIVE);
	}

	@Test
	@DisplayName("list: 계약을 뷰로 매핑(라벨 포함)")
	void list() {
		given(policies.findByOwnerIdOrderByContractDateDesc(owner))
				.willReturn(List.of(p("A", LocalDate.of(2020, 1, 1))));

		List<PolicyView> views = listMyPoliciesService.list(owner);

		assertThat(views).singleElement().satisfies(v -> {
			assertThat(v.policyNumber()).isEqualTo("A");
			assertThat(v.planTypeLabel()).isEqualTo("종신보험");
			assertThat(v.statusLabel()).isEqualTo("정상");
		});
	}

	@Test
	@DisplayName("seedFor: 계약이 없으면 데모 3건 저장")
	void seedWhenEmpty() {
		given(policies.existsByOwnerId(owner)).willReturn(false);

		seedDemoPoliciesService.seedFor(owner, "김철수");

		verify(policies).saveAll(saved.capture());
		assertThat(saved.getValue()).hasSize(3)
				.allSatisfy(pol -> assertThat(pol.getInsuredName()).isEqualTo("김철수"));
	}

	@Test
	@DisplayName("seedFor: 이미 계약이 있으면 아무것도 하지 않음")
	void seedNoopWhenExists() {
		given(policies.existsByOwnerId(owner)).willReturn(true);

		seedDemoPoliciesService.seedFor(owner, "김철수");

		verify(policies, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
	}

	@Test
	@DisplayName("seedFor: ownerId 가 null 이면 조회도 하지 않음")
	void seedNoopWhenNullOwner() {
		seedDemoPoliciesService.seedFor(null, "김철수");

		verify(policies, never()).existsByOwnerId(org.mockito.ArgumentMatchers.any());
		verify(policies, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
	}
}
