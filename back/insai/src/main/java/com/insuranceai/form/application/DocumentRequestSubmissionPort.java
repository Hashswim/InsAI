package com.insuranceai.form.application;

import java.util.UUID;

/**
 * 양식 제출이 성립하면 collab 쪽 서류 요청/케이스 상태를 전이시키는 포트.
 * 구현은 form.infrastructure 에서 collab 서비스로 위임한다.
 */
public interface DocumentRequestSubmissionPort {

	void onFormSubmitted(UUID documentRequestId, UUID submittedBy);
}
