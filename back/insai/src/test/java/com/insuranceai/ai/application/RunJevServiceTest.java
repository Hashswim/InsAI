package com.insuranceai.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.ai.domain.*;
import com.insuranceai.collab.application.CaseAccessService;
import com.insuranceai.document.application.ReadClaimPdfService;
import com.insuranceai.common.error.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RunJevServiceTest {
    final CaseAccessService access = mock(CaseAccessService.class);
    final ReadClaimPdfService files = mock(ReadClaimPdfService.class);
    final JevGateway gateway = mock(JevGateway.class);
    final JevAnalysisRepository repository = mock(JevAnalysisRepository.class);
    final JevAccessAuditService audit = mock(JevAccessAuditService.class);
    final RunJevService service = new RunJevService(access, files, gateway, repository, new ObjectMapper(), audit);

    @Test void forbiddenCaseNeverCallsModel() {
        UUID user = UUID.randomUUID(), caseId=UUID.randomUUID();
        doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(access).requireParticipant(caseId,user);
        assertThrows(BusinessException.class, () -> service.run(user, new RunJevCommand(caseId,List.of())));
        verifyNoInteractions(gateway, files, repository);
    }

    @Test void duplicateTypesNeverCallModel() {
        var ref=new RunJevCommand.FileRef(UUID.randomUUID(),RunJevCommand.Kind.DIAGNOSIS);
        assertThrows(BusinessException.class, () -> service.run(UUID.randomUUID(),
            new RunJevCommand(UUID.randomUUID(),List.of(ref,ref,ref))));
        verifyNoInteractions(gateway, repository);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ErrorCode.class,
        names = {"INTERNAL_ERROR", "AI_INVALID_RESPONSE", "AI_TIMEOUT", "AI_SERVICE_UNAVAILABLE", "AI_RATE_LIMITED"})
    void failureDoesNotSaveResult(ErrorCode code) {
        var refs=Arrays.stream(RunJevCommand.Kind.values())
            .map(kind -> new RunJevCommand.FileRef(UUID.randomUUID(),kind)).toList();
        when(files.read(any(),any(),any())).thenReturn("%PDF-fake".getBytes());
        when(gateway.analyze(any())).thenThrow(new BusinessException(code));
        assertThrows(BusinessException.class, () -> service.run(UUID.randomUUID(),
            new RunJevCommand(UUID.randomUUID(),refs)));
        verifyNoInteractions(repository);
    }

    @Test void missingAuditPersistenceNeverCallsModel() {
        var refs=Arrays.stream(RunJevCommand.Kind.values())
            .map(kind -> new RunJevCommand.FileRef(UUID.randomUUID(),kind)).toList();
        when(files.read(any(),any(),any())).thenReturn("%PDF-synthetic".getBytes());
        doThrow(new IllegalStateException("audit unavailable")).when(audit)
            .record(any(),any(),any(),isNull(),any(),eq(com.insuranceai.ai.domain.JevAccessAudit.Outcome.ALLOWED),any());
        assertThrows(IllegalStateException.class,() -> service.run(UUID.randomUUID(),
            new RunJevCommand(UUID.randomUUID(),refs)));
        verifyNoInteractions(gateway,repository);
    }
}
