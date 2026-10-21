package com.insuranceai.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.ai.domain.*;
import com.insuranceai.auth.application.AccessTokenIssuer;
import com.insuranceai.auth.domain.*;
import com.insuranceai.collab.domain.*;
import com.insuranceai.common.error.*;
import com.insuranceai.document.application.ReadClaimPdfService;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JevJobIntegrationTest {
    @Autowired JevJobService service;
    @Autowired JevJobStore store;
    @Autowired JevJobWorker worker;
    @Autowired JevJobRepository jobs;
    @Autowired JevAnalysisRepository analyses;
    @Autowired AccountRepository accounts;
    @Autowired CaseRepository cases;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired AccessTokenIssuer tokens;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean JevExecutionEventRepository events;
    @Autowired JevAccessAuditRepository audits;
    @MockitoBean ReadClaimPdfService files;
    @MockitoBean JevGateway gateway;
    Account user, outsider;
    Case claim;
    RunJevCommand command;

    @BeforeEach void setup() {
        jobs.deleteAll();
        user=accounts.save(Account.create(UUID.randomUUID()+"@test.invalid","hash","synthetic",Role.CLAIMANT));
        outsider=accounts.save(Account.create(UUID.randomUUID()+"@test.invalid","hash","synthetic",Role.CLAIMANT));
        claim=cases.save(Case.open(user.getId(),"synthetic job"));
        command=new RunJevCommand(claim.getId(),Arrays.stream(RunJevCommand.Kind.values())
            .map(k -> new RunJevCommand.FileRef(UUID.randomUUID(),k)).toList());
        when(files.read(any(),any(),any())).thenReturn("%PDF-synthetic".getBytes());
        when(gateway.analyze(any())).thenAnswer(call -> result(((JevGateway.Request) call.getArgument(0)).requestId()));
    }
    JevResult result(UUID id) {
        return new JevResult(id,"ON_HOLD","NOT_REGISTERED","rules missing",
            List.of(new JevResult.Item("synthetic","UNVERIFIABLE",List.of(),0,"synthetic","review")),
            "gemini","synthetic-model","jev-v2","a".repeat(64),"jev-chain-v7",null,"DISABLED",0,0,
            new JevResult.Verification("STRUCTURAL","NOT_VERIFIED","MODEL_SELF_ASSESSMENT","REQUIRED"));
    }
    String bearer(Account actor) { return "Bearer "+tokens.issue(actor.getId(),actor.getRole()).value(); }
    JevJobService.JobView submit(UUID key) { return service.submit(user.getId(),key,UUID.randomUUID(),command); }

    @Test void asynchronousHttpCycleStoresResultAndSupportsPolling() throws Exception {
        UUID requestId=UUID.randomUUID();
        var response=mvc.perform(post("/ai/jev/jobs").header("Authorization",bearer(user))
            .header("Idempotency-Key",UUID.randomUUID()).header("X-Request-ID",requestId)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command)))
            .andExpect(status().isAccepted()).andExpect(header().exists("Location"))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.data.status").value("QUEUED")).andReturn();
        UUID id=UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).path("data").path("jobId").asText());
        verifyNoInteractions(gateway);
        worker.tick();
        var view=service.get(user.getId(),id);
        assertEquals(JevJob.Status.SUCCEEDED,view.status());
        assertEquals(requestId,view.requestId());
        assertNotNull(view.startedAt()); assertNotNull(view.finishedAt());
        assertTrue(analyses.findById(view.analysisId()).isPresent());
        mvc.perform(get("/ai/jev/jobs/"+id).header("Authorization",bearer(user)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        mvc.perform(get("/ai/jev/"+view.analysisId()).header("Authorization",bearer(user)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.result.request_id").value(requestId.toString()));
        worker.tick();
        verify(gateway,times(1)).analyze(any());
    }

    @Test void sameKeyIsIdempotentEvenWithReorderedDocumentsAndDifferentRequestId() {
        UUID key=UUID.randomUUID();
        var first=submit(key);
        var reversed=new ArrayList<>(command.documents()); Collections.reverse(reversed);
        var second=service.submit(user.getId(),key,UUID.randomUUID(),new RunJevCommand(claim.getId(),reversed));
        assertEquals(first.jobId(),second.jobId()); assertEquals(first.requestId(),second.requestId());
        assertEquals(1,jobs.count());
        verifyNoInteractions(gateway);
        verify(files,times(3)).read(any(),any(),any());
    }

    @Test void reusedKeyWithDifferentInputConflicts() {
        UUID key=UUID.randomUUID(); submit(key);
        var changed=new ArrayList<>(command.documents());
        changed.set(0,new RunJevCommand.FileRef(UUID.randomUUID(),RunJevCommand.Kind.DIAGNOSIS));
        var error=assertThrows(BusinessException.class,() -> service.submit(user.getId(),key,UUID.randomUUID(),
            new RunJevCommand(claim.getId(),changed)));
        assertEquals(ErrorCode.CONFLICT,error.getErrorCode()); assertEquals(1,jobs.count());
    }

    @Test void outsiderCannotSubmitOrReadJob() throws Exception {
        var view=submit(UUID.randomUUID());
        mvc.perform(get("/ai/jev/jobs/"+view.jobId()).header("Authorization",bearer(outsider)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/ai/jev/jobs").header("Authorization",bearer(outsider)).header("Idempotency-Key",UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command)))
            .andExpect(status().isForbidden());
        assertEquals(1,jobs.count()); verifyNoInteractions(gateway);
    }

    @Test void authenticationAndCanonicalIdempotencyKeyAreRequired() throws Exception {
        mvc.perform(post("/ai/jev/jobs").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command)))
            .andExpect(status().isUnauthorized());
        for (String key : List.of("SENTINEL_INVALID","1-1-1-1-1"," ")) {
            mvc.perform(post("/ai/jev/jobs").header("Authorization",bearer(user)).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command)))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/ai/jev/jobs").header("Authorization",bearer(user))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command)))
            .andExpect(status().isBadRequest());
        assertEquals(0,jobs.count()); verifyNoInteractions(files,gateway);
    }

    @Test void workerFailurePersistsOnlyCodeAndNeverRetriesOrStoresAnalysis() {
        var key=UUID.randomUUID(); var queued=submit(key); long before=analyses.count();
        doThrow(new BusinessException(ErrorCode.AI_TIMEOUT,"SENTINEL_PATIENT")).when(gateway).analyze(any());
        worker.tick(); worker.tick();
        var view=service.get(user.getId(),queued.jobId());
        assertEquals(JevJob.Status.FAILED,view.status()); assertEquals("AI_TIMEOUT",view.errorCode());
        assertNull(view.analysisId()); assertEquals(before,analyses.count());
        assertEquals(queued.jobId(),submit(key).jobId());
        verify(gateway,times(1)).analyze(any());
        assertFalse(jobs.findById(queued.jobId()).orElseThrow().getCommandJson().contains("PDF"));
    }

    @Test void filesAreReauthorizedBeforeWorkerCallsModel() {
        var queued=submit(UUID.randomUUID());
        when(files.read(any(),any(),any())).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
        worker.tick();
        assertEquals("FORBIDDEN",service.get(user.getId(),queued.jobId()).errorCode());
        verifyNoInteractions(gateway);
    }

    @Test void queuedJobSurvivesWorkerRecreation() {
        var queued=submit(UUID.randomUUID());
        var restarted=new JevJobWorker(store,runner,mapper,accounts);
        restarted.tick();
        assertEquals(JevJob.Status.SUCCEEDED,service.get(user.getId(),queued.jobId()).status());
    }
    @Autowired RunJevService runner;

    @Test void suspendedRequesterNeverCallsModel() {
        var queued=submit(UUID.randomUUID());
        clearInvocations(files);
        user.suspend(); accounts.save(user);
        worker.tick();
        assertEquals(JevJob.Status.FAILED,jobs.findById(queued.jobId()).orElseThrow().getStatus());
        assertEquals("FORBIDDEN",jobs.findById(queued.jobId()).orElseThrow().getErrorCode());
        verifyNoInteractions(gateway,files);
    }

    @Test void staleRunningJobBecomesUnknownAndCannotBeReplayedOrCompleted() {
        var queued=submit(UUID.randomUUID()); var claimed=store.claim().orElseThrow();
        assertEquals(queued.jobId(),claimed.getId()); assertTrue(store.claim().isEmpty());
        assertEquals(1,store.expire(Instant.now().plusSeconds(901)));
        long before=analyses.count();
        store.complete(queued.jobId(),result(claimed.getRequestId()));
        worker.tick();
        var view=service.get(user.getId(),queued.jobId());
        assertEquals(JevJob.Status.UNKNOWN,view.status()); assertEquals("EXECUTION_UNCERTAIN",view.errorCode());
        assertEquals(before,analyses.count()); verifyNoInteractions(gateway);
    }

    @Test void resultAndSuccessStatusRollBackTogether() {
        var queued=submit(UUID.randomUUID()); var claimed=store.claim().orElseThrow(); long before=analyses.count();
        assertThrows(IllegalStateException.class,() -> new TransactionTemplate(transactions).execute(status -> {
            store.complete(queued.jobId(),result(claimed.getRequestId()));
            throw new IllegalStateException("synthetic transaction failure");
        }));
        assertEquals(before,analyses.count());
        assertEquals(JevJob.Status.RUNNING,jobs.findById(queued.jobId()).orElseThrow().getStatus());
        assertEquals(2,events.findTop100ByJobIdOrderByCreatedAtAscIdAsc(queued.jobId()).size());
    }

    @Test void resultMetadataAndAuthorizedHistoryLinkTheWholeJob() throws Exception {
        var queued=submit(UUID.randomUUID()); worker.tick();
        var view=service.get(user.getId(),queued.jobId());
        var analysis=analyses.findById(view.analysisId()).orElseThrow();
        assertEquals(queued.requestId(),analysis.getRequestId()); assertEquals(queued.jobId(),analysis.getJobId());
        assertEquals("gemini",analysis.getProvider()); assertEquals("synthetic-model",analysis.getModel());
        assertEquals("jev-v2",analysis.getPromptVersion()); assertEquals("a".repeat(64),analysis.getPromptSha256());
        assertEquals("jev-chain-v7",analysis.getChainVersion()); assertNull(analysis.getTraceId());
        assertEquals("DISABLED",analysis.getTraceStatus());
        var rows=events.findTop100ByJobIdOrderByCreatedAtAscIdAsc(queued.jobId());
        assertEquals(4,rows.size());
        assertEquals(Set.of(JevExecutionEvent.Type.ENQUEUED,JevExecutionEvent.Type.STARTED,
            JevExecutionEvent.Type.RESULT_STORED,JevExecutionEvent.Type.SUCCEEDED),
            rows.stream().map(JevExecutionEvent::getType).collect(java.util.stream.Collectors.toSet()));
        for (String path : List.of("/ai/jev/jobs/"+queued.jobId()+"/history","/ai/jev/"+view.analysisId()+"/history")) {
            var response=mvc.perform(get(path).header("Authorization",bearer(user)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.length()").value(4)).andReturn().getResponse().getContentAsString();
            assertFalse(response.contains("synthetic-model")); assertFalse(response.contains("confidence"));
            assertFalse(response.contains("verification_reason")); assertFalse(response.contains("resultJson"));
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            UUID denied=UUID.randomUUID();
            mvc.perform(get(path).header("Authorization",bearer(outsider)).header("X-Request-ID",denied))
                .andExpect(status().isForbidden());
            assertEquals(queued.jobId(),audits.findByRequestId(denied).getFirst().getJobId());
        }
    }

    @Test void failedAndUnknownEventsAreNotRepeatedOrMixedWithAccessAudit() {
        var queued=submit(UUID.randomUUID());
        doThrow(new BusinessException(ErrorCode.AI_TIMEOUT,"SENTINEL_PATIENT")).when(gateway).analyze(any());
        worker.tick(); worker.tick();
        var failed=events.findTop100ByJobIdOrderByCreatedAtAscIdAsc(queued.jobId());
        assertEquals(3,failed.size());
        assertEquals(1,failed.stream().filter(e -> e.getType()==JevExecutionEvent.Type.FAILED).count());
        assertTrue(failed.stream().noneMatch(e -> e.getType()==JevExecutionEvent.Type.RESULT_STORED));
        assertTrue(failed.stream().allMatch(e -> e.getErrorCode()==null || e.getErrorCode().equals("AI_TIMEOUT")));
        var second=submit(UUID.randomUUID()); store.claim();
        store.expire(Instant.now().plusSeconds(901)); store.expire(Instant.now().plusSeconds(902));
        var unknown=events.findTop100ByJobIdOrderByCreatedAtAscIdAsc(second.jobId());
        assertEquals(3,unknown.size());
        assertEquals(1,unknown.stream().filter(e -> e.getType()==JevExecutionEvent.Type.UNKNOWN).count());
    }

    @Test void sharedRequestIdDoesNotMergeDifferentJobsHistories() throws Exception {
        UUID shared=UUID.randomUUID();
        var first=service.submit(user.getId(),UUID.randomUUID(),shared,command);
        var second=service.submit(user.getId(),UUID.randomUUID(),shared,command);
        worker.tick(); worker.tick();
        var analysis=service.get(user.getId(),first.jobId()).analysisId();
        var response=mvc.perform(get("/ai/jev/"+analysis+"/history").header("Authorization",bearer(user)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(4))
            .andReturn().getResponse().getContentAsString();
        assertTrue(response.contains(first.jobId().toString())); assertFalse(response.contains(second.jobId().toString()));
    }

    @Test void synchronousSaveAndEventCommitTogetherAndFailureIsCodeOnly() {
        UUID requestId=UUID.randomUUID();
        var view=runner.run(user.getId(),command,requestId);
        var analysis=analyses.findById(view.analysisId()).orElseThrow();
        assertEquals(requestId,analysis.getRequestId()); assertNull(analysis.getJobId());
        assertEquals(1,events.findTop100ByAnalysisIdOrderByCreatedAtAscIdAsc(view.analysisId()).size());
        long count=analyses.count(); UUID failedRequest=UUID.randomUUID();
        doThrow(new BusinessException(ErrorCode.AI_INVALID_RESPONSE,"SENTINEL_PATIENT")).when(gateway).analyze(any());
        assertThrows(BusinessException.class,() -> runner.run(user.getId(),command,failedRequest));
        assertEquals(count,analyses.count());
        var failed=events.findTop100ByRequestIdOrderByCreatedAtAscIdAsc(failedRequest);
        assertEquals(1,failed.size()); assertEquals(JevExecutionEvent.Type.FAILED,failed.getFirst().getType());
        assertEquals("AI_INVALID_RESPONSE",failed.getFirst().getErrorCode()); assertNull(failed.getFirst().getAnalysisId());
    }

    @Test void synchronousResultIsRolledBackWhenItsAuditCannotBeSaved() {
        long before=analyses.count(); UUID requestId=UUID.randomUUID();
        doThrow(new IllegalStateException("SENTINEL_AUDIT_FAILURE")).when(events)
            .save(argThat(e -> e!=null && e.getType()==JevExecutionEvent.Type.RESULT_STORED));
        var error=assertThrows(BusinessException.class,() -> runner.run(user.getId(),command,requestId));
        assertEquals(ErrorCode.INTERNAL_ERROR,error.getErrorCode()); assertNull(error.getCause());
        assertFalse(error.getMessage().contains("SENTINEL")); assertEquals(before,analyses.count());
        var rows=events.findTop100ByRequestIdOrderByCreatedAtAscIdAsc(requestId);
        assertEquals(1,rows.size()); assertEquals(JevExecutionEvent.Type.FAILED,rows.getFirst().getType());
    }

    @Test void asynchronousResultIsRolledBackWhenItsAuditCannotBeSaved() {
        var queued=submit(UUID.randomUUID()); long before=analyses.count();
        doThrow(new IllegalStateException("SENTINEL_AUDIT_FAILURE")).when(events)
            .save(argThat(e -> e!=null && e.getType()==JevExecutionEvent.Type.RESULT_STORED));
        worker.tick();
        assertEquals(before,analyses.count());
        assertEquals(JevJob.Status.FAILED,jobs.findById(queued.jobId()).orElseThrow().getStatus());
        var rows=events.findTop100ByJobIdOrderByCreatedAtAscIdAsc(queued.jobId());
        assertEquals(3,rows.size()); assertTrue(rows.stream().noneMatch(e -> e.getType()==JevExecutionEvent.Type.RESULT_STORED));
    }

    @Test void historicalRowsDoNotAcquireInventedMetadataOrEvents() throws Exception {
        var legacy=analyses.save(JevAnalysis.create(claim.getId(),user.getId(),"{\"prompt_version\":\"jev-v1\"}"));
        assertNull(legacy.getRequestId()); assertNull(legacy.getPromptVersion()); assertNull(legacy.getChainVersion());
        mvc.perform(get("/ai/jev/"+legacy.getId()+"/history").header("Authorization",bearer(user)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test void metricsAreCaseScopedAggregateOnlyAndTraceDisabledIsNotFailure() throws Exception {
        submit(UUID.randomUUID()); worker.tick();
        analyses.save(JevAnalysis.create(claim.getId(),user.getId(),"SENTINEL_LEGACY_RESULT"));
        var another=cases.save(Case.open(user.getId(),"other synthetic claim"));
        analyses.save(JevAnalysis.create(another.getId(),user.getId(),"SENTINEL_OTHER_CASE"));
        var response=mvc.perform(get("/ai/jev/cases/"+claim.getId()+"/metrics").header("Authorization",bearer(user)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.data.storedAnalyses").value(2))
            .andExpect(jsonPath("$.data.tokenMetadataRows").value(1))
            .andExpect(jsonPath("$.data.inputTokens").value(0))
            .andExpect(jsonPath("$.data.traceStatuses.DISABLED").value(1))
            .andExpect(jsonPath("$.data.traceStatuses.UNKNOWN").value(1))
            .andExpect(jsonPath("$.data.traceStatuses.FAILED").value(0))
            .andExpect(jsonPath("$.data.jobStatuses.SUCCEEDED").value(1))
            .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("SENTINEL")); assertFalse(response.contains("analysisId"));
        assertFalse(response.contains(claim.getId().toString())); assertFalse(response.contains(user.getId().toString()));
        mvc.perform(get("/ai/jev/cases/"+claim.getId()+"/metrics").header("Authorization",bearer(outsider)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/ai/jev/cases/"+claim.getId()+"/metrics")).andExpect(status().isUnauthorized());
        for (int days : List.of(0,32)) mvc.perform(get("/ai/jev/cases/"+claim.getId()+"/metrics?days="+days)
            .header("Authorization",bearer(user))).andExpect(status().isBadRequest());
    }

    @Test void emptyCaseMetricsAreZeroRatherThanFabricatedUsage() throws Exception {
        mvc.perform(get("/ai/jev/cases/"+claim.getId()+"/metrics?days=1").header("Authorization",bearer(user)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.storedAnalyses").value(0))
            .andExpect(jsonPath("$.data.tokenMetadataRows").value(0))
            .andExpect(jsonPath("$.data.inputTokens").value(0))
            .andExpect(jsonPath("$.data.outputTokens").value(0));
    }

    @Test void metricsWindowExcludesOlderResultsAndSumsReportedTokens() throws Exception {
        var base=result(UUID.randomUUID());
        var first=analyses.save(JevAnalysis.captured(claim.getId(),user.getId(),new JevAnalysis.Provenance(
            base.requestId(),null,"gemini","synthetic","jev-v2","a".repeat(64),"jev-chain-v7",null,"DISABLED",10,20),"{}"));
        jdbc.update("update jev_analyses set created_at=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(172800)),first.getId());
        mvc.perform(get("/ai/jev/cases/"+claim.getId()+"/metrics?days=1").header("Authorization",bearer(user)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.storedAnalyses").value(0));
        mvc.perform(get("/ai/jev/cases/"+claim.getId()+"/metrics?days=7").header("Authorization",bearer(user)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.inputTokens").value(10))
            .andExpect(jsonPath("$.data.outputTokens").value(20));
    }

    @Test void concurrentClaimsHaveOnlyOneWinner() throws Exception {
        submit(UUID.randomUUID());
        try (var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Boolean> task=() -> { start.await(); return store.claim().isPresent(); };
            var first=pool.submit(task); var second=pool.submit(task); start.countDown();
            assertEquals(1,(first.get(5,TimeUnit.SECONDS)?1:0)+(second.get(5,TimeUnit.SECONDS)?1:0));
        }
    }

    @Test void concurrentDuplicateSubmissionsCreateOneDurableJob() throws Exception {
        UUID key=UUID.randomUUID();
        try (var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<UUID> task=() -> { start.await(); return submit(key).jobId(); };
            var first=pool.submit(task); var second=pool.submit(task); start.countDown();
            assertEquals(first.get(5,TimeUnit.SECONDS),second.get(5,TimeUnit.SECONDS));
            assertEquals(1,jobs.count());
        }
    }
}
