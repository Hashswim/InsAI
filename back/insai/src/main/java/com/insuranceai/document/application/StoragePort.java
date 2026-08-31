package com.insuranceai.document.application;

/**
 * 오브젝트 스토리지 포트. 구현: LocalDiskStorage(로컬), 이후 MinIO/S3 presigner.
 */
public interface StoragePort {

	/** 업로드용 URL (클라이언트가 이 URL 로 PUT). */
	String uploadUrl(String storageKey);

	/** 다운로드용 URL. */
	String downloadUrl(String storageKey);
}
