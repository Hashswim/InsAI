package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.CaseStatus;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.support.fixture.CollabFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CaseAccessServiceTest {

	@Mock CaseRepository cases;
	@InjectMocks CaseAccessService caseAccessService;

	private final UUID caseId = UUID.randomUUID();
	private final UUID claimant = UUID.randomUUID();
	private final UUID adjuster = UUID.randomUUID();

	@Test
	@DisplayName("없는 케이스 -> NOT_FOUND")
	void notFound() {
		given(cases.findById(caseId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> caseAccessService.requireParticipant(caseId, claimant))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("청구인/배정 사정사면 통과, 그 외는 FORBIDDEN")
	void participant() {
		Case c = CollabFixtures.caseAt(CaseStatus.OPEN, claimant, adjuster);
		given(cases.findById(caseId)).willReturn(Optional.of(c));

		assertThatCode(() -> caseAccessService.requireParticipant(caseId, claimant)).doesNotThrowAnyException();
		assertThatCode(() -> caseAccessService.requireParticipant(caseId, adjuster)).doesNotThrowAnyException();
		assertThatThrownBy(() -> caseAccessService.requireParticipant(caseId, UUID.randomUUID()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
	}
}
