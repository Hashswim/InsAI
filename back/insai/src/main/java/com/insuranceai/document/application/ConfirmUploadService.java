package com.insuranceai.document.application;

import com.insuranceai.document.domain.StoredFile;
import com.insuranceai.document.domain.StoredFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConfirmUploadService {

	private static final Logger log = LoggerFactory.getLogger(ConfirmUploadService.class);

	private final StoredFileRepository files;
	private final OcrPort ocr;
	private final CaseParticipantGuard participantGuard;

	public ConfirmUploadService(StoredFileRepository files, OcrPort ocr, CaseParticipantGuard participantGuard) {
		this.files = files;
		this.ocr = ocr;
		this.participantGuard = participantGuard;
	}

	/** 업로드 완료를 확정해 메타데이터를 저장하고, 가능하면 OCR 텍스트를 붙인다. */
	@Transactional
	public UUID confirm(ConfirmUploadCommand c) {
		participantGuard.requireParticipant(c.caseId(), c.uploaderId());
		StoredFile file = StoredFile.register(c.caseId(), c.submissionId(), c.uploaderId(),
				c.filename(), c.contentType(), c.sizeBytes(), c.storageKey());
		try {
			String text = ocr.extractText(c.storageKey());
			if (text != null && !text.isBlank()) {
				file.attachOcrText(text);
			}
		} catch (RuntimeException e) {
			log.warn("OCR 추출 실패 — 텍스트 없이 저장. key={}", c.storageKey(), e);
		}
		return files.save(file).getId();
	}
}
