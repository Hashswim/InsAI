package com.insuranceai.document.api;

import com.insuranceai.document.infrastructure.LocalDiskStorage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 디스크 스토리지의 업로드/다운로드 엔드포인트. 운영에서는 오브젝트 스토리지 presigned URL 로 대체.
 */
@RestController
public class LocalStorageController {

	private final LocalDiskStorage storage;

	public LocalStorageController(LocalDiskStorage storage) {
		this.storage = storage;
	}

	@PutMapping("/local-storage/{*storageKey}")
	public ResponseEntity<Void> put(@PathVariable String storageKey, @RequestBody byte[] body) {
		storage.write(trimLeadingSlash(storageKey), body);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/local-storage/{*storageKey}")
	public ResponseEntity<byte[]> get(@PathVariable String storageKey) {
		byte[] bytes = storage.read(trimLeadingSlash(storageKey));
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.body(bytes);
	}

	private static String trimLeadingSlash(String s) {
		return s.startsWith("/") ? s.substring(1) : s;
	}
}
