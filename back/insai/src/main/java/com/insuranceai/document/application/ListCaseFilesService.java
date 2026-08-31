package com.insuranceai.document.application;

import com.insuranceai.document.domain.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListCaseFilesService {

	private final StoredFileRepository files;

	public ListCaseFilesService(StoredFileRepository files) {
		this.files = files;
	}

	@Transactional(readOnly = true)
	public List<StoredFileView> byCase(UUID caseId) {
		return files.findByCaseIdOrderByCreatedAtAsc(caseId).stream()
				.map(StoredFileView::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<StoredFileView> bySubmission(UUID submissionId) {
		return files.findBySubmissionIdOrderByCreatedAtAsc(submissionId).stream()
				.map(StoredFileView::from)
				.toList();
	}
}
