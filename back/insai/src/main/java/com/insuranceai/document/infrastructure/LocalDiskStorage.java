package com.insuranceai.document.infrastructure;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.document.DocumentProperties;
import com.insuranceai.document.application.StoragePort;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 로컬 디스크 스토리지. 바이트는 {@code localDir/<storageKey>} 에 저장하고,
 * URL 은 {@code <publicBaseUrl>/<storageKey>} 로 발급한다(LocalStorageController 가 처리).
 */
@Component
public class LocalDiskStorage implements StoragePort {

	private final Path root;
	private final String publicBaseUrl;

	public LocalDiskStorage(DocumentProperties props) {
		this.root = Path.of(props.localDir()).toAbsolutePath().normalize();
		this.publicBaseUrl = stripTrailingSlash(props.publicBaseUrl());
	}

	@Override
	public String uploadUrl(String storageKey) {
		return publicBaseUrl + "/" + storageKey;
	}

	@Override
	public String downloadUrl(String storageKey) {
		return publicBaseUrl + "/" + storageKey;
	}

	public void write(String storageKey, byte[] bytes) {
		Path target = resolve(storageKey);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, bytes);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public byte[] read(String storageKey) {
		Path target = resolve(storageKey);
		if (!Files.exists(target)) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "파일이 없습니다.");
		}
		try {
			return Files.readAllBytes(target);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Path resolve(String storageKey) {
		Path target = root.resolve(storageKey).normalize();
		if (!target.startsWith(root)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 저장 경로입니다.");
		}
		return target;
	}

	private static String stripTrailingSlash(String s) {
		return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
	}
}
