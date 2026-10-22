package com.insuranceai.document;

import com.insuranceai.document.domain.StoredFile;
import com.insuranceai.document.domain.StoredFileRepository;
import com.insuranceai.support.AbstractJpaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StoredFileRepositoryJpaTest extends AbstractJpaTest {

	@Autowired StoredFileRepository files;

	private final UUID caseId = UUID.randomUUID();
	private final UUID submissionId = UUID.randomUUID();
	private final UUID uploader = UUID.randomUUID();

	@Test
	@DisplayName("findByCaseIdOrderByCreatedAtAsc: 생성 순서대로")
	void byCase() throws InterruptedException {
		files.save(StoredFile.register(caseId, null, uploader, "first.pdf", "application/pdf", 10, caseId + "/1.pdf"));
		Thread.sleep(5);
		files.save(StoredFile.register(caseId, submissionId, uploader, "second.pdf", "application/pdf", 20, caseId + "/2.pdf"));
		files.save(StoredFile.register(UUID.randomUUID(), null, uploader, "other.pdf", "application/pdf", 30, "other/3.pdf"));

		List<StoredFile> result = files.findByCaseIdOrderByCreatedAtAsc(caseId);

		assertThat(result).extracting(StoredFile::getFilename).containsExactly("first.pdf", "second.pdf");
	}

	@Test
	@DisplayName("findBySubmissionIdOrderByCreatedAtAsc: 해당 제출물 첨부만")
	void bySubmission() {
		files.save(StoredFile.register(caseId, submissionId, uploader, "a.pdf", "application/pdf", 10, caseId + "/a.pdf"));
		files.save(StoredFile.register(caseId, null, uploader, "b.pdf", "application/pdf", 10, caseId + "/b.pdf"));

		assertThat(files.findBySubmissionIdOrderByCreatedAtAsc(submissionId))
				.extracting(StoredFile::getFilename).containsExactly("a.pdf");
	}
}
