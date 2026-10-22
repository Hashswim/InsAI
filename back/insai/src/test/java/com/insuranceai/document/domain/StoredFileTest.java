package com.insuranceai.document.domain;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoredFileTest {

	private final UUID caseId = UUID.randomUUID();
	private final UUID uploader = UUID.randomUUID();

	@Test
	@DisplayName("register: 허용 확장자 + 유효 크기면 메타데이터 생성, isNew")
	void registerOk() {
		StoredFile f = StoredFile.register(caseId, null, uploader,
				"진단서.PDF", "application/pdf", 1234, caseId + "/x.pdf");

		assertThat(f.getCaseId()).isEqualTo(caseId);
		assertThat(f.getUploaderId()).isEqualTo(uploader);
		assertThat(f.getFilename()).isEqualTo("진단서.PDF");
		assertThat(f.getSizeBytes()).isEqualTo(1234);
		assertThat(f.isNew()).isTrue();
	}

	@Test
	@DisplayName("register: 허용되지 않는 확장자 -> INVALID_INPUT")
	void badExtension() {
		assertThatThrownBy(() -> StoredFile.register(caseId, null, uploader,
				"악성.exe", "application/octet-stream", 10, "k"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
		assertThatThrownBy(() -> StoredFile.validateUploadable("진단서.jpg", 10))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("validateUploadable: 크기 0 이하 / 상한 초과 -> INVALID_INPUT")
	void badSize() {
		assertThatThrownBy(() -> StoredFile.validateUploadable("a.pdf", 0))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
		assertThatThrownBy(() -> StoredFile.validateUploadable("a.pdf", StoredFile.MAX_SIZE_BYTES + 1))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("extensionOf: 마지막 점 뒤 소문자, 점 없으면 빈 문자열")
	void extensionOf() {
		assertThat(StoredFile.extensionOf("a.b.JPG")).isEqualTo("jpg");
		assertThat(StoredFile.extensionOf("noext")).isEmpty();
		assertThat(StoredFile.extensionOf(null)).isEmpty();
	}
}
