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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CaseQueryServiceTest {

	@Mock
	CaseRepository cases;

	@Mock
	ParticipantNames names;

	@InjectMocks
	ListCasesService listCasesService;

	@InjectMocks
	GetCaseService getCaseService;

	private final UUID me = UUID.randomUUID();
	private final UUID other = UUID.randomUUID();

	@Test
	@DisplayName("forUser: 청구인/사정사로 겹치는 케이스는 중복 제거된다")
	void forUserDedups() {
		Case both = CollabFixtures.caseAt(CaseStatus.DOCS_REQUESTED, me, me);
		given(cases.findByClaimantId(me)).willReturn(List.of(both));
		given(cases.findByAdjusterId(me)).willReturn(List.of(both));

		List<CaseView> result = listCasesService.forUser(me);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).id()).isEqualTo(both.getId());
	}

	@Test
	@DisplayName("unassigned: adjusterId 가 있는 OPEN 케이스는 제외한다")
	void unassignedFiltersAssigned() {
		Case open = CollabFixtures.caseAt(CaseStatus.OPEN, other, null);
		Case openButAssigned = CollabFixtures.caseAt(CaseStatus.OPEN, other, me);
		given(cases.findByStatus(CaseStatus.OPEN)).willReturn(List.of(open, openButAssigned));

		List<CaseView> result = listCasesService.unassigned();

		assertThat(result).extracting(CaseView::id).containsExactly(open.getId());
	}

	@Test
	@DisplayName("GetCaseService: 없는 케이스면 NOT_FOUND")
	void getNotFound() {
		UUID caseId = UUID.randomUUID();
		given(cases.findById(caseId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> getCaseService.get(caseId))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}
}
