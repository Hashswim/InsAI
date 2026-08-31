package com.insuranceai.policy.domain;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyTest {

	private final UUID owner = UUID.randomUUID();

	private Policy sample(String insurer) {
		return Policy.register(owner, insurer, "평생든든 종신보험", "HL-1",
				PolicyPlanType.WHOLE_LIFE, "홍길동",
				LocalDate.of(2016, 3, 10), LocalDate.of(2016, 3, 10), null,
				100_000_000L, 182_000L, "사망 1억", PolicyStatus.ACTIVE);
	}

	@Test
	@DisplayName("register: 필수값 채우면 생성, isNew")
	void registerOk() {
		Policy p = sample("한화생명");

		assertThat(p.getOwnerId()).isEqualTo(owner);
		assertThat(p.getInsurerName()).isEqualTo("한화생명");
		assertThat(p.getPlanType()).isEqualTo(PolicyPlanType.WHOLE_LIFE);
		assertThat(p.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
		assertThat(p.getCoverageEndDate()).isNull();
		assertThat(p.isNew()).isTrue();
	}

	@Test
	@DisplayName("register: 보험사/상품명/증권번호/피보험자 공백 -> INVALID_INPUT")
	void blankText() {
		assertThatThrownBy(() -> sample("  "))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}
}
