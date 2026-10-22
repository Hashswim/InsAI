package com.insuranceai.document.application;

import com.insuranceai.document.domain.StoredFileRepository;
import com.insuranceai.document.infrastructure.LocalDiskStorage;
import com.insuranceai.common.error.*;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ReadClaimPdfService {
    private final StoredFileRepository files;
    private final CaseParticipantGuard guard;
    private final LocalDiskStorage storage;
    public ReadClaimPdfService(StoredFileRepository files, CaseParticipantGuard guard, LocalDiskStorage storage) {
        this.files=files; this.guard=guard; this.storage=storage;
    }
    public byte[] read(UUID caseId, UUID userId, UUID fileId) {
        guard.requireParticipant(caseId, userId);
        var file = files.findById(fileId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!file.getCaseId().equals(caseId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!file.getContentType().equals("application/pdf") || file.getSizeBytes() > 10L*1024*1024)
            throw new BusinessException(ErrorCode.INVALID_INPUT, "10MB 이하 PDF만 분석할 수 있습니다.");
        byte[] bytes = storage.read(file.getStorageKey());
        if (bytes.length > 10*1024*1024 || bytes.length < 5
            || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효한 PDF가 아닙니다.");
        return bytes;
    }
}
