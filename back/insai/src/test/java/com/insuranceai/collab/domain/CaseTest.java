package com.insuranceai.collab.domain;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.support.fixture.CollabFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseTest {

	private final UUID claimant = UUID.randomUUID();
	private final UUID adjuster = UUID.randomUUID();

	@Test
	@DisplayName("open: OPEN 상태, 청구인 세팅, 사정사 미배정")
	void open() {
		Case c = Case.open(claimant, "자동차 사고");

		assertThat(c.getStatus()).isEqualTo(CaseStatus.OPEN);
		assertThat(c.getClaimantId()).isEqualTo(claimant);
		assertThat(c.getAdjusterId()).isNull();
	}

	@Test
	@DisplayName("open: 제목이 공백이면 INVALID_INPUT")
	void openRejectsBlankTitle() {
		assertThatThrownBy(() -> Case.open(claimant, "   "))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("assignAdjuster: 사정사를 배정한다")
	void assignAdjuster() {
		Case c = Case.open(claimant, "자동차 사고");

		c.assignAdjuster(adjuster);

		assertThat(c.getAdjusterId()).isEqualTo(adjuster);
	}

	@Test
	@DisplayName("assignAdjuster: 이미 배정되어 있으면 CONFLICT")
	void assignAdjusterTwice() {
		Case c = Case.open(claimant, "자동차 사고");
		c.assignAdjuster(adjuster);

		assertThatThrownBy(() -> c.assignAdjuster(UUID.randomUUID()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
	}

	@Test
	@DisplayName("정상 전이: OPEN -> DOCS_REQUESTED -> DOCS_SUBMITTED -> UNDER_REVIEW -> DECISION_MADE -> CLOSED")
	void happyPathTransitions() {
		Case c = Case.open(claimant, "자동차 사고");
		c.assignAdjuster(adjuster);

		c.markDocsRequested();
		assertThat(c.getStatus()).isEqualTo(CaseStatus.DOCS_REQUESTED);
		c.markDocsSubmitted();
		assertThat(c.getStatus()).isEqualTo(CaseStatus.DOCS_SUBMITTED);
		c.startReview();
		assertThat(c.getStatus()).isEqualTo(CaseStatus.UNDER_REVIEW);
		c.recordDecision();
		assertThat(c.getStatus()).isEqualTo(CaseStatus.DECISION_MADE);
		c.close();
		assertThat(c.getStatus()).isEqualTo(CaseStatus.CLOSED);
	}

	@Test
	@DisplayName("markDocsSubmitted: DOCS_REQUESTED 가 아니면 CONFLICT")
	void markDocsSubmittedFromWrongState() {
		Case c = Case.open(claimant, "자동차 사고");

		assertThatThrownBy(c::markDocsSubmitted)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
	}

	@Test
	@DisplayName("CLOSED 케이스에서 markDocsRequested 는 CONFLICT")
	void requestDocsOnClosedCase() {
		Case c = CollabFixtures.caseAt(CaseStatus.CLOSED, claimant, adjuster);

		assertThatThrownBy(c::markDocsRequested)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
	}

	@Test
	@DisplayName("requireParticipant: 비참여자면 FORBIDDEN")
	void requireParticipantRejectsOutsider() {
		Case c = CollabFixtures.caseAt(CaseStatus.OPEN, claimant, adjuster);

		assertThatThrownBy(() -> c.requireParticipant(UUID.randomUUID()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("requireParticipant: 청구인/사정사는 통과")
	void requireParticipantAllowsMembers() {
		Case c = CollabFixtures.caseAt(CaseStatus.OPEN, claimant, adjuster);

		assertThatCode(() -> c.requireParticipant(claimant)).doesNotThrowAnyException();
		assertThatCode(() -> c.requireParticipant(adjuster)).doesNotThrowAnyException();
	}
}
