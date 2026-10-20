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
    final RunJevService service = new RunJevService(access, files, gateway, repository, new ObjectMapper());

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

    @Test void failureDoesNotSaveResult() {
        var refs=Arrays.stream(RunJevCommand.Kind.values())
            .map(kind -> new RunJevCommand.FileRef(UUID.randomUUID(),kind)).toList();
        when(files.read(any(),any(),any())).thenReturn("%PDF-fake".getBytes());
        when(gateway.analyze(any())).thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR));
        assertThrows(BusinessException.class, () -> service.run(UUID.randomUUID(),
            new RunJevCommand(UUID.randomUUID(),refs)));
        verifyNoInteractions(repository);
    }
}
