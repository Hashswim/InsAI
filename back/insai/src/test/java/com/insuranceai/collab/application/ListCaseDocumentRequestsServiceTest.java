package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.DocumentRequest;
import com.insuranceai.collab.domain.DocumentRequestRepository;
import com.insuranceai.support.fixture.CollabFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ListCaseDocumentRequestsServiceTest {

	@Mock
	DocumentRequestRepository documentRequests;

	@InjectMocks
	ListCaseDocumentRequestsService service;

	@Test
	@DisplayName("케이스의 서류 요청을 뷰로 매핑해 반환한다")
	void mapsToViews() {
		UUID caseId = UUID.randomUUID();
		DocumentRequest request = CollabFixtures.submittedDocRequest();
		given(documentRequests.findByCaseIdOrderByCreatedAtAsc(caseId)).willReturn(List.of(request));

		List<DocumentRequestView> views = service.list(caseId);

		assertThat(views).hasSize(1);
		assertThat(views.get(0).status()).isEqualTo("SUBMITTED");
		assertThat(views.get(0).title()).isEqualTo("진단서");
	}
}
