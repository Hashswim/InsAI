package com.insuranceai.document.application;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.document.domain.StoredFile;
import com.insuranceai.document.domain.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class IssueDownloadUrlService {

	private final StoredFileRepository files;
	private final CaseParticipantGuard participantGuard;
	private final StoragePort storage;

	public IssueDownloadUrlService(StoredFileRepository files,
	                               CaseParticipantGuard participantGuard,
	                               StoragePort storage) {
		this.files = files;
		this.participantGuard = participantGuard;
		this.storage = storage;
	}

	/**
	 * @throws BusinessException NOT_FOUND - 없는 파일, FORBIDDEN - 케이스 비참여자
	 */
	@Transactional(readOnly = true)
	public String issue(UUID fileId, UUID requesterId) {
		StoredFile file = files.findById(fileId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "파일을 찾을 수 없습니다."));
		participantGuard.requireParticipant(file.getCaseId(), requesterId);
		return storage.downloadUrl(file.getStorageKey());
	}
}
