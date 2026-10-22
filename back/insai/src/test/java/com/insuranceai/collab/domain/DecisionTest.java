package com.insuranceai.collab.domain;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionTest {

	private final UUID caseId = UUID.randomUUID();
	private final UUID adjusterId = UUID.randomUUID();

	@Test
	@DisplayName("of: rawText 를 보관한다")
	void of() {
		Decision decision = Decision.of(caseId, adjusterId, "지급 승인. 총 320만원.");

		assertThat(decision.getRawText()).contains("지급 승인");
	}

	@Test
	@DisplayName("of: rawText 가 공백이면 INVALID_INPUT")
	void ofRejectsBlankRawText() {
		assertThatThrownBy(() -> Decision.of(caseId, adjusterId, "   "))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}
}
