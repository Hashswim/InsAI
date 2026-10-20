package com.insuranceai.ai.application;

import com.insuranceai.auth.domain.*;
import com.insuranceai.collab.domain.*;
import com.insuranceai.document.application.ReadClaimPdfService;
import com.insuranceai.ai.domain.JevAnalysisRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class JevPersistenceIntegrationTest {
    @Autowired RunJevService service;
    @Autowired AccountRepository accounts;
    @Autowired CaseRepository cases;
    @Autowired JevAnalysisRepository analyses;
    @MockitoBean ReadClaimPdfService files;
    @MockitoBean JevGateway gateway;

    @Test void storesAndReloadsGroundsModelAndTrace() {
        var account=accounts.save(Account.create(UUID.randomUUID()+"@test.invalid","hash","test",Role.CLAIMANT));
        var claim=cases.save(Case.open(account.getId(),"synthetic medical claim"));
        var refs=Arrays.stream(RunJevCommand.Kind.values())
            .map(kind -> new RunJevCommand.FileRef(UUID.randomUUID(),kind)).toList();
        UUID traceId=UUID.randomUUID();
        when(files.read(any(),any(),any())).thenReturn("%PDF-synthetic".getBytes());
        when(gateway.analyze(any())).thenAnswer(call -> {
            JevGateway.Request request=call.getArgument(0);
            return new JevResult(request.requestId(),"ON_HOLD","NOT_REGISTERED","rules missing",
                List.of(new JevResult.Item("date","SUITABLE",
                    List.of(new JevResult.Evidence(refs.getFirst().fileId(),1,"date present")),
                    0.8,"readable","")),
                "gemini","fake-model","jev-v1",traceId,"RECORDED",10,20);
        });
        var saved=service.run(account.getId(),new RunJevCommand(claim.getId(),refs));
        var loaded=service.get(account.getId(),saved.analysisId()).result();
        assertEquals(traceId,loaded.traceId());
        assertEquals("fake-model",loaded.model());
        assertEquals(1,loaded.items().getFirst().evidence().getFirst().page());
        assertEquals("ON_HOLD",loaded.submissionStatus());
        assertTrue(analyses.findById(saved.analysisId()).isPresent());
        assertThrows(com.insuranceai.common.error.BusinessException.class,
            () -> service.get(UUID.randomUUID(),saved.analysisId()));
    }
}
