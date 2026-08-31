package com.insuranceai.document.domain;

import com.insuranceai.common.domain.BaseEntity;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 업로드된 파일의 메타데이터. 실제 바이트는 storageKey 로 스토리지에, OCR 텍스트는 추출 후 부착.
 */
@Entity
@Table(name = "stored_files",
		uniqueConstraints = @UniqueConstraint(name = "uk_stored_files_storage_key", columnNames = "storage_key"))
public class StoredFile extends BaseEntity {

	public static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;
	public static final Set<String> ALLOWED_EXTENSIONS =
			Set.of("pdf", "jpg", "jpeg", "png", "heic", "heif", "tif", "tiff", "webp");

	@Column(nullable = false)
	private UUID caseId;

	@Column
	private UUID submissionId;

	@Column(nullable = false)
	private UUID uploaderId;

	@Column(nullable = false)
	private String filename;

	@Column(nullable = false, length = 120)
	private String contentType;

	@Column(nullable = false)
	private long sizeBytes;

	@Column(name = "storage_key", nullable = false)
	private String storageKey;

	@Column(length = 20000)
	private String ocrText;

	protected StoredFile() {
	}

	public static StoredFile register(UUID caseId, UUID submissionId, UUID uploaderId,
	                                  String filename, String contentType, long sizeBytes, String storageKey) {
		validateUploadable(filename, sizeBytes);
		StoredFile f = new StoredFile();
		f.assignId();
		f.caseId = Objects.requireNonNull(caseId, "caseId");
		f.submissionId = submissionId;
		f.uploaderId = Objects.requireNonNull(uploaderId, "uploaderId");
		f.filename = requireText(filename, "파일명");
		f.contentType = requireText(contentType, "contentType");
		f.sizeBytes = sizeBytes;
		f.storageKey = requireText(storageKey, "storageKey");
		return f;
	}

	/** 업로드 전 검증에도 재사용. 확장자/용량 위반 시 INVALID_INPUT. */
	public static void validateUploadable(String filename, long sizeBytes) {
		String ext = extensionOf(filename);
		if (!ALLOWED_EXTENSIONS.contains(ext)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않는 파일 형식입니다: " + ext);
		}
		if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"파일 크기는 1B 이상 " + (MAX_SIZE_BYTES / 1024 / 1024) + "MB 이하여야 합니다.");
		}
	}

	public void attachOcrText(String text) {
		this.ocrText = text;
	}

	public static String extensionOf(String filename) {
		if (filename == null) {
			return "";
		}
		int dot = filename.lastIndexOf('.');
		return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
	}

	public UUID getCaseId() {
		return caseId;
	}

	public UUID getSubmissionId() {
		return submissionId;
	}

	public UUID getUploaderId() {
		return uploaderId;
	}

	public String getFilename() {
		return filename;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getOcrText() {
		return ocrText;
	}
}
