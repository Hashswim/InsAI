package com.insuranceai.ai.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.ai.application.*;
import com.insuranceai.ai.domain.*;
import com.insuranceai.auth.application.AccessTokenIssuer;
import com.insuranceai.auth.domain.*;
import com.insuranceai.auth.infrastructure.JwtAccessTokenIssuer;
import com.insuranceai.collab.domain.*;
import com.insuranceai.document.domain.*;
import com.insuranceai.document.infrastructure.LocalDiskStorage;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JevSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccessTokenIssuer tokens;
    @Autowired RSAKey signingKey;
    @Autowired AccountRepository accounts;
    @Autowired CaseRepository cases;
    @Autowired StoredFileRepository files;
    @Autowired JevAnalysisRepository analyses;
    @Autowired JevAccessAuditRepository audits;
    @MockitoBean LocalDiskStorage storage;
    @MockitoBean JevGateway gateway;
    Account claimant, outsider, adjuster;
    Case claim;
    List<StoredFile> documents;

    @BeforeEach void seed() {
        claimant=account(Role.CLAIMANT);
        outsider=account(Role.CLAIMANT);
        adjuster=account(Role.ADJUSTER);
        adjuster.activate();
        adjuster=accounts.save(adjuster);
        claim=Case.open(claimant.getId(),"synthetic medical claim");
        claim.assignAdjuster(adjuster.getId());
        claim=cases.save(claim);
        documents=new ArrayList<>();
        for (int index=0;index<3;index++) documents.add(file(claim.getId(),claimant.getId()));
        when(storage.read(anyString())).thenReturn("%PDF-synthetic".getBytes());
        when(gateway.analyze(any())).thenAnswer(call -> {
            JevGateway.Request request=call.getArgument(0);
            return new JevResult(request.requestId(),"ON_HOLD","NOT_REGISTERED","rules missing",
                List.of(new JevResult.Item("synthetic","UNVERIFIABLE",List.of(),0,"test only","review")),
                "gemini","synthetic-model","jev-v1","a".repeat(64),"jev-chain-v1",null,"DISABLED",0,0);
        });
    }

    @Test void validJwtDeterminesActorAndIgnoresSpoofedHeader() throws Exception {
        UUID requestId=UUID.randomUUID();
        mvc.perform(post("/ai/jev").header("Authorization",bearer(claimant))
            .header("X-User-Id",outsider.getId()).header("X-Request-ID",requestId)
            .contentType(MediaType.APPLICATION_JSON).content(body(documents)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        var rows=audits.findByRequestId(requestId);
        assertEquals(1,rows.size());
        assertEquals(claimant.getId(),rows.getFirst().getActorId());
        assertEquals(JevAccessAudit.Outcome.ALLOWED,rows.getFirst().getOutcome());
        verify(gateway).analyze(argThat(request -> request.requestId().equals(requestId)));
    }

    @Test void outsiderCannotImpersonateParticipantAndDenialIsPersisted() throws Exception {
        UUID requestId=UUID.randomUUID();
        mvc.perform(post("/ai/jev").header("Authorization",bearer(outsider))
            .header("X-User-Id",claimant.getId()).header("X-Request-ID",requestId)
            .contentType(MediaType.APPLICATION_JSON).content(body(documents)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(gateway,storage);
        var rows=audits.findByRequestId(requestId);
        assertEquals(1,rows.size());
        assertEquals(outsider.getId(),rows.getFirst().getActorId());
        assertEquals(JevAccessAudit.Outcome.DENIED,rows.getFirst().getOutcome());
    }

    @Test void headerAloneOrServiceTokenDoesNotAuthenticateUser() throws Exception {
        mvc.perform(post("/ai/jev").header("X-User-Id",claimant.getId())
            .contentType(MediaType.APPLICATION_JSON).content(body(documents)))
            .andExpect(status().isUnauthorized()).andExpect(header().exists("X-Request-ID"));
        mvc.perform(post("/ai/jev").header("Authorization","Bearer internal-service-token")
            .contentType(MediaType.APPLICATION_JSON).content(body(documents)))
            .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate","Bearer"));
        verifyNoInteractions(gateway,storage);
    }

    @Test void fileInAnotherCaseIsDeniedEvenWhenOwnerIsSame() throws Exception {
        var otherCase=cases.save(Case.open(claimant.getId(),"another synthetic claim"));
        var foreign=file(otherCase.getId(),claimant.getId());
        var mixed=new ArrayList<>(documents);
        mixed.set(0,foreign);
        UUID requestId=UUID.randomUUID();
        mvc.perform(post("/ai/jev").header("Authorization",bearer(claimant))
            .header("X-Request-ID",requestId).contentType(MediaType.APPLICATION_JSON).content(body(mixed)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(gateway,storage);
        assertEquals("FORBIDDEN",audits.findByRequestId(requestId).getFirst().getReasonCode());
    }

    @Test void assignedAdjusterCanReadButOutsiderCannot() throws Exception {
        var analysis=analyses.save(JevAnalysis.create(claim.getId(),claimant.getId(),
            mapper.writeValueAsString(gateway.analyze(new JevGateway.Request(UUID.randomUUID(),List.of())))));
        clearInvocations(gateway);
        UUID allowedId=UUID.randomUUID(), deniedId=UUID.randomUUID();
        mvc.perform(get("/ai/jev/"+analysis.getId()).header("Authorization",bearer(adjuster))
            .header("X-Request-ID",allowedId)).andExpect(status().isOk());
        mvc.perform(get("/ai/jev/"+analysis.getId()).header("Authorization",bearer(outsider))
            .header("X-Request-ID",deniedId)).andExpect(status().isForbidden());
        assertEquals(JevAccessAudit.Action.READ,audits.findByRequestId(allowedId).getFirst().getAction());
        assertEquals(JevAccessAudit.Outcome.DENIED,audits.findByRequestId(deniedId).getFirst().getOutcome());
        verifyNoInteractions(gateway,storage);
    }

    @Test void inactiveAccountsAndAdminHaveNoImplicitClinicalAccess() throws Exception {
        String existing=bearer(claimant);
        claimant.suspend();
        accounts.save(claimant);
        var pending=account(Role.ADJUSTER);
        var admin=account(Role.ADMIN);
        mvc.perform(post("/ai/jev").header("Authorization",existing)
            .contentType(MediaType.APPLICATION_JSON).content(body(documents))).andExpect(status().isUnauthorized());
        mvc.perform(post("/ai/jev").header("Authorization",bearer(pending))
            .contentType(MediaType.APPLICATION_JSON).content(body(documents))).andExpect(status().isUnauthorized());
        mvc.perform(post("/ai/jev").header("Authorization",bearer(admin))
            .contentType(MediaType.APPLICATION_JSON).content(body(documents))).andExpect(status().isForbidden());
        verifyNoInteractions(gateway,storage);
    }

    @Test void expiredOrTamperedJwtCannotCallModel() throws Exception {
        var oldIssuer=new JwtAccessTokenIssuer(signingKey,"insai-auth",Duration.ofMinutes(15),
            Clock.fixed(Instant.now().minusSeconds(3600),ZoneOffset.UTC));
        String expired=oldIssuer.issue(claimant.getId(),Role.CLAIMANT).value();
        String valid=tokens.issue(claimant.getId(),Role.CLAIMANT).value();
        int signature=valid.lastIndexOf('.')+1;
        String tampered=valid.substring(0,signature)+(valid.charAt(signature)=='a'?'b':'a')+valid.substring(signature+1);
        for (String token:List.of(expired,tampered)) {
            mvc.perform(post("/ai/jev").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content(body(documents)))
                .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(gateway,storage);
    }

    @Test void duplicateAuthorizationHeadersAreRejected() throws Exception {
        mvc.perform(post("/ai/jev").header("Authorization",bearer(claimant),bearer(outsider))
            .contentType(MediaType.APPLICATION_JSON).content(body(documents)))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(gateway,storage);
    }

    private Account account(Role role) {
        return accounts.save(Account.create(UUID.randomUUID()+"@test.invalid","hash","synthetic",role));
    }
    private StoredFile file(UUID caseId, UUID uploader) {
        return files.save(StoredFile.register(caseId,null,uploader,"synthetic.pdf","application/pdf",14,
            UUID.randomUUID().toString()));
    }
    private String bearer(Account account) { return "Bearer "+tokens.issue(account.getId(),account.getRole()).value(); }
    private String body(List<StoredFile> selected) throws Exception {
        var kinds=RunJevCommand.Kind.values();
        var refs=new ArrayList<Map<String,Object>>();
        for(int index=0;index<3;index++) refs.add(Map.of("fileId",selected.get(index).getId(),"kind",kinds[index]));
        return mapper.writeValueAsString(Map.of("caseId",claim.getId(),"documents",refs));
    }
}
