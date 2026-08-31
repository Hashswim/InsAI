package com.insuranceai.document.application;

import com.insuranceai.document.domain.StoredFile;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IssueUploadUrlService {

	private final StoragePort storage;
	private final CaseParticipantGuard participantGuard;

	public IssueUploadUrlService(StoragePort storage, CaseParticipantGuard participantGuard) {
		this.storage = storage;
		this.participantGuard = participantGuard;
	}

	/** 케이스 참여자 확인 → 확장자/용량 검증 → 업로드 대상(URL + storageKey) 발급. */
	public UploadTarget issue(IssueUploadUrlCommand command) {
		participantGuard.requireParticipant(command.caseId(), command.uploaderId());
		StoredFile.validateUploadable(command.filename(), command.sizeBytes());
		String ext = StoredFile.extensionOf(command.filename());
		String key = command.caseId() + "/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
		return new UploadTarget(storage.uploadUrl(key), key);
	}
}
