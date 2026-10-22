package com.insuranceai.document.application;

import com.insuranceai.document.domain.StoredFile;
import com.insuranceai.document.domain.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConfirmUploadService {

	private final StoredFileRepository files;
	private final CaseParticipantGuard participantGuard;

	public ConfirmUploadService(StoredFileRepository files, CaseParticipantGuard participantGuard) {
		this.files = files;
		this.participantGuard = participantGuard;
	}

	/** 업로드 완료를 확정해 메타데이터를 저장한다. */
	@Transactional
	public UUID confirm(ConfirmUploadCommand c) {
		participantGuard.requireParticipant(c.caseId(), c.uploaderId());
		StoredFile file = StoredFile.register(c.caseId(), c.submissionId(), c.uploaderId(),
				c.filename(), c.contentType(), c.sizeBytes(), c.storageKey());
		return files.save(file).getId();
	}
}
