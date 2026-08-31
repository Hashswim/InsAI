package com.insuranceai.document.api;

import java.util.UUID;

public final class DocumentResponses {

	private DocumentResponses() {
	}

	public record UploadUrlIssued(String url, String storageKey) {
	}

	public record FileStored(UUID fileId) {
	}

	public record DownloadUrl(String url) {
	}
}
