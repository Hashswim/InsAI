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
	@DisplayName("of: rawText 를 보관하고 plainText 는 아직 없다")
	void of() {
		Decision decision = Decision.of(caseId, adjusterId, "지급 승인. 총 320만원.");

		assertThat(decision.getRawText()).contains("지급 승인");
		assertThat(decision.getPlainText()).isNull();
	}

	@Test
	@DisplayName("of: rawText 가 공백이면 INVALID_INPUT")
	void ofRejectsBlankRawText() {
		assertThatThrownBy(() -> Decision.of(caseId, adjusterId, "   "))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("applyPlainText: 평문 번역을 설정한다")
	void applyPlainText() {
		Decision decision = Decision.of(caseId, adjusterId, "원문");

		decision.applyPlainText("고객님의 청구가 승인되었습니다.");

		assertThat(decision.getPlainText()).isEqualTo("고객님의 청구가 승인되었습니다.");
	}
}
