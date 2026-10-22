package com.insuranceai.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.ai.domain.*;
import com.insuranceai.auth.application.AccessTokenIssuer;
import com.insuranceai.auth.domain.*;
import com.insuranceai.collab.domain.*;
import com.insuranceai.document.domain.*;
import com.insuranceai.document.infrastructure.LocalDiskStorage;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** No mocked gateway, file storage, HTTP server or business repositories. */
@Tag("cross-service")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JevCrossServiceTest {
    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) {
        String url=System.getenv("CROSS_SERVICE_BASE_URL");
        String token=System.getenv("CROSS_SERVICE_TOKEN");
        String storage=System.getenv("CROSS_SERVICE_STORAGE");
        if (url==null || !url.matches("http://127\\.0\\.0\\.1:[0-9]{1,5}") || token==null || storage==null)
            throw new IllegalStateException("Run through make cross-service");
        properties.add("insai.jev.base-url",() -> url);
        properties.add("insai.jev.service-token",() -> token);
        properties.add("insai.jev.allow-stub",() -> true);
        properties.add("insai.jev.jobs.worker-enabled",() -> false);
        properties.add("insai.storage.local-dir",() -> storage);
        properties.add("spring.datasource.url",() -> "jdbc:h2:mem:jev-cross-service;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        properties.add("logging.level.root",() -> "WARN");
        properties.add("logging.level.com.insuranceai",() -> "WARN");
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired AccessTokenIssuer tokens;
    @Autowired AccountRepository accounts;
    @Autowired CaseRepository cases;
    @Autowired StoredFileRepository files;
    @Autowired LocalDiskStorage storage;
    @Autowired JevAnalysisRepository analyses;
    @Autowired JevExecutionEventRepository events;
    @Autowired JevAccessAuditRepository audits;
    @Autowired JevJobWorker worker;
    final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    Account user;
    Case claim;
    RunJevCommand command;

    @BeforeEach void seed() throws Exception {
        user=accounts.save(Account.create(UUID.randomUUID()+"@test.invalid","synthetic-hash","synthetic",Role.CLAIMANT));
        claim=cases.save(Case.open(user.getId(),"synthetic cross-service claim"));
        byte[] pdf=Files.readAllBytes(Path.of(System.getenv("CROSS_SERVICE_PDF")));
        var refs=new ArrayList<RunJevCommand.FileRef>();
        for (var kind : RunJevCommand.Kind.legacyValues()) {
            String key=UUID.randomUUID()+".pdf";
            storage.write(key,pdf);
            var file=files.save(StoredFile.register(claim.getId(),null,user.getId(),"synthetic.pdf","application/pdf",pdf.length,key));
            refs.add(new RunJevCommand.FileRef(file.getId(),kind));
        }
        command=new RunJevCommand(claim.getId(),refs);
    }
    HttpResponse<String> send(String method, String path, Object body, UUID requestId, UUID key) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
            .timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+tokens.issue(user.getId(),user.getRole()).value())
            .header("X-Request-ID",requestId.toString());
        if (key!=null) builder.header("Idempotency-Key",key.toString());
        if (body==null) builder.GET();
        else builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode data(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status,response.statusCode());
        assertEquals("no-store",response.headers().firstValue("Cache-Control").orElseThrow());
        return mapper.readTree(response.body()).path("data");
    }
    void checkResult(JsonNode result, UUID requestId) {
        assertEquals(requestId.toString(),result.path("request_id").asText());
        assertEquals("ON_HOLD",result.path("submission_status").asText());
        assertEquals("NOT_REGISTERED",result.path("regulations_status").asText());
        assertEquals("stub",result.path("provider").asText());
        assertEquals("DISABLED",result.path("trace_status").asText()); assertTrue(result.path("trace_id").isNull());
        assertEquals("OFFLINE_STUB",result.path("verification").path("confidence_basis").asText());
        assertEquals("REQUIRED",result.path("verification").path("human_review").asText());
        assertFalse(result.path("items").isEmpty());
        for (var item : result.path("items")) {
            assertEquals("UNVERIFIABLE",item.path("judgment").asText());
            assertEquals(0,item.path("confidence").asDouble()); assertTrue(item.path("evidence").isEmpty());
        }
    }
    @Test void synchronousRealHttpCyclePersistsResultAuditAndMetrics() throws Exception {
        UUID requestId=UUID.randomUUID();
        var created=data(send("POST","/ai/jev",command,requestId,null),200);
        checkResult(created.path("result"),requestId);
        UUID id=UUID.fromString(created.path("analysisId").asText());
        var loaded=data(send("GET","/ai/jev/"+id,null,UUID.randomUUID(),null),200);
        assertEquals(created,loaded);
        assertEquals(requestId,analyses.findById(id).orElseThrow().getRequestId());
        assertEquals(1,events.findTop100ByAnalysisIdOrderByCreatedAtAscIdAsc(id).size());
        var history=data(send("GET","/ai/jev/"+id+"/history",null,UUID.randomUUID(),null),200);
        assertEquals("RESULT_STORED",history.get(0).path("type").asText());
        var metrics=data(send("GET","/ai/jev/cases/"+claim.getId()+"/metrics",null,UUID.randomUUID(),null),200);
        assertEquals(1,metrics.path("storedAnalyses").asLong());
        assertEquals(1,metrics.path("traceStatuses").path("DISABLED").asLong());
    }
    @Test void asynchronousRealHttpCycleIsIdempotentAndStoresFourEvents() throws Exception {
        UUID requestId=UUID.randomUUID(), key=UUID.randomUUID();
        var queued=data(send("POST","/ai/jev/jobs",command,requestId,key),202);
        String jobId=queued.path("jobId").asText();
        assertEquals("QUEUED",queued.path("status").asText());
        var reused=data(send("POST","/ai/jev/jobs",command,UUID.randomUUID(),key),202);
        assertEquals(jobId,reused.path("jobId").asText());
        worker.tick();
        var done=data(send("GET","/ai/jev/jobs/"+jobId,null,UUID.randomUUID(),null),200);
        assertEquals("SUCCEEDED",done.path("status").asText());
        var result=data(send("GET","/ai/jev/"+done.path("analysisId").asText(),null,UUID.randomUUID(),null),200);
        checkResult(result.path("result"),requestId);
        assertEquals(4,events.findTop100ByJobIdOrderByCreatedAtAscIdAsc(UUID.fromString(jobId)).size());
    }
    @Test void malformedPdfRejectedByRealFastApiDoesNotStoreResult() throws Exception {
        var first=files.findById(command.documents().getFirst().fileId()).orElseThrow();
        storage.write(first.getStorageKey(),"%PDF-synthetic-invalid".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        long before=analyses.count(); UUID requestId=UUID.randomUUID();
        var response=send("POST","/ai/jev",command,requestId,null);
        assertEquals(400,response.statusCode());
        assertEquals("INVALID_INPUT",mapper.readTree(response.body()).path("error").path("code").asText());
        assertEquals(before,analyses.count());
        var failed=events.findTop100ByRequestIdOrderByCreatedAtAscIdAsc(requestId);
        assertEquals(JevExecutionEvent.Type.FAILED,failed.getFirst().getType());
    }
    @Test void actualHttpAuthenticationAndCaseAuthorizationAreRequired() throws Exception {
        long before=analyses.count();
        var unauthenticated=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/ai/jev"))
            .timeout(Duration.ofSeconds(10)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(command))).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(401,unauthenticated.statusCode());
        user=accounts.save(Account.create(UUID.randomUUID()+"@test.invalid","synthetic-hash","outsider",Role.CLAIMANT));
        UUID denied=UUID.randomUUID();
        assertEquals(403,send("POST","/ai/jev",command,denied,null).statusCode());
        assertEquals(before,analyses.count());
        assertEquals(JevAccessAudit.Outcome.DENIED,audits.findByRequestId(denied).getFirst().getOutcome());
    }
}
