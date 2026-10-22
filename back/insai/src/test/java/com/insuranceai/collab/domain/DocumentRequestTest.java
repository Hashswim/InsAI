package com.insuranceai.collab.domain;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentRequestTest {

	private final UUID caseId = UUID.randomUUID();
	private final UUID requester = UUID.randomUUID();
	private final UUID templateId = UUID.randomUUID();

	private DocumentRequest submitted() {
		DocumentRequest request = DocumentRequest.create(caseId, requester, templateId, "진단서", null);
		request.markSubmitted();
		return request;
	}

	@Test
	@DisplayName("create: PENDING 상태로 생성된다")
	void createIsPending() {
		DocumentRequest request = DocumentRequest.create(caseId, requester, templateId, "진단서", null);

		assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.PENDING);
		assertThat(request.getCaseId()).isEqualTo(caseId);
		assertThat(request.getTemplateId()).isEqualTo(templateId);
	}

	@Test
	@DisplayName("markSubmitted: PENDING -> SUBMITTED")
	void markSubmitted() {
		DocumentRequest request = DocumentRequest.create(caseId, requester, templateId, "진단서", null);

		request.markSubmitted();

		assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.SUBMITTED);
	}

	@Test
	@DisplayName("accept: SUBMITTED -> ACCEPTED")
	void accept() {
		DocumentRequest request = submitted();

		request.accept();

		assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.ACCEPTED);
	}

	@Test
	@DisplayName("reject: 사유와 함께 REJECTED")
	void reject() {
		DocumentRequest request = submitted();

		request.reject("판독이 불가능합니다");

		assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.REJECTED);
		assertThat(request.getRejectionReason()).isEqualTo("판독이 불가능합니다");
	}

	@Test
	@DisplayName("reject: 사유가 공백이면 INVALID_INPUT")
	void rejectWithoutReason() {
		DocumentRequest request = submitted();

		assertThatThrownBy(() -> request.reject("   "))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("reject 후 재제출 허용")
	void resubmitAfterReject() {
		DocumentRequest request = submitted();
		request.reject("판독 불가");

		request.markSubmitted();

		assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.SUBMITTED);
	}

	@Test
	@DisplayName("requestSupplement: 사유와 함께 SUPPLEMENT_REQUESTED, 수정 후 재제출 허용")
	void supplementThenResubmit() {
		DocumentRequest request = submitted();

		request.requestSupplement("질병분류기호 기재 후 재발급해 주세요");

		assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.SUPPLEMENT_REQUESTED);
		assertThat(request.getRejectionReason()).isEqualTo("질병분류기호 기재 후 재발급해 주세요");
		request.markSubmitted();
		assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.SUBMITTED);
		assertThat(request.getRejectionReason()).isNull();
	}

	@Test
	@DisplayName("requestSupplement: 사유가 공백이거나 제출 전이면 거절")
	void supplementRequiresReasonAndSubmission() {
		assertThatThrownBy(() -> submitted().requestSupplement(" "))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
		DocumentRequest accepted = submitted();
		accepted.accept();
		assertThatThrownBy(() -> accepted.requestSupplement("보완"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
	}
}
