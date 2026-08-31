package com.insuranceai.document.infrastructure;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.document.DocumentProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDiskStorageTest {

	@TempDir
	Path tmp;

	private LocalDiskStorage storage() {
		return new LocalDiskStorage(new DocumentProperties(tmp.toString(), "/local-storage"));
	}

	@Test
	@DisplayName("write -> read 라운드트립, 중첩 디렉터리 생성")
	void roundTrip() {
		LocalDiskStorage s = storage();
		s.write("case-1/file.pdf", "hello".getBytes());

		assertThat(s.read("case-1/file.pdf")).asString().isEqualTo("hello");
	}

	@Test
	@DisplayName("uploadUrl / downloadUrl 은 publicBaseUrl + key")
	void urls() {
		LocalDiskStorage s = storage();
		assertThat(s.uploadUrl("k/f.pdf")).isEqualTo("/local-storage/k/f.pdf");
		assertThat(s.downloadUrl("k/f.pdf")).isEqualTo("/local-storage/k/f.pdf");
	}

	@Test
	@DisplayName("루트 밖으로 벗어나는 키(../) -> INVALID_INPUT")
	void pathTraversal() {
		LocalDiskStorage s = storage();
		assertThatThrownBy(() -> s.write("../evil.pdf", "x".getBytes()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("없는 키 read -> NOT_FOUND")
	void missing() {
		assertThatThrownBy(() -> storage().read("nope/x.pdf"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
	}
}
