package com.insuranceai.ai.infrastructure;

import com.insuranceai.ai.application.JevGateway;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FastApiJevClientTest {
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"gemini", "openai"})
    void sendsAuthenticationAndParsesSnakeCase(String provider) throws Exception {
        UUID requestId=UUID.randomUUID(), traceId=UUID.randomUUID();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/jev", exchange -> {
            assertEquals("Bearer test-token",exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals(requestId.toString(), exchange.getRequestHeaders().getFirst("X-Request-ID"));
            String request=new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(request.contains("request_id"));
            String response="""
                {"request_id":"%s","submission_status":"ON_HOLD","regulations_status":"NOT_REGISTERED",
                 "reason":"rules missing","items":[{"item":"date","judgment":"UNVERIFIABLE","evidence":[],
                 "confidence":0,"verification_reason":"unreadable","supplement":"rescan"}],
                 "provider":"%s","model":"fake","prompt_version":"jev-v1","trace_id":"%s",
                 "prompt_sha256":"%s","chain_version":"jev-chain-v1",
                 "trace_status":"RECORDED","input_tokens":10,"output_tokens":20,
                 "verification":{"method":"STRUCTURAL","factual_consistency":"NOT_VERIFIED",
                 "confidence_basis":"MODEL_SELF_ASSESSMENT","human_review":"REQUIRED"}}
                """.formatted(requestId,provider,traceId,"a".repeat(64));
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client=new FastApiJevClient(new FastApiJevProperties(
                "http://127.0.0.1:"+server.getAddress().getPort(),"test-token",5));
            var result=client.analyze(new JevGateway.Request(requestId,List.of()));
            assertEquals(traceId,result.traceId());
            assertEquals(provider,result.provider());
            assertEquals("ON_HOLD",result.submissionStatus());
            assertEquals(10,result.inputTokens());
            assertEquals("a".repeat(64),result.promptSha256());
            assertEquals("jev-chain-v1",result.chainVersion());
            assertEquals("NOT_VERIFIED",result.verification().factualConsistency());
        } finally { server.stop(0); }
    }

    @Test void missingTokenFailsWithoutNetwork() {
        var client=new FastApiJevClient(new FastApiJevProperties("http://localhost:1","",1));
        assertThrows(BusinessException.class, () -> client.analyze(
            new JevGateway.Request(UUID.randomUUID(),List.of())));
    }

    @ParameterizedTest
    @CsvSource({"prompt_version,true", "prompt_sha256,true", "chain_version,true",
        "prompt_version,false", "prompt_sha256,false", "chain_version,false"})
    void rejectsMissingOrInvalidReleaseIdentity(String field, boolean missing) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
            java.nio.file.Files.readString(java.nio.file.Path.of(
                "../../docs/ai/contracts/jev-response-example.json")));
        UUID requestId = UUID.randomUUID();
        payload.put("request_id", requestId.toString());
        if (missing) payload.remove(field);
        else payload.put(field, field.equals("prompt_sha256") ? "not-a-sha256" : " ");
        byte[] bytes = mapper.writeValueAsBytes(payload);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/jev", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client = new FastApiJevClient(new FastApiJevProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), "token", 5));
            var error = assertThrows(BusinessException.class, () -> client.analyze(
                new JevGateway.Request(requestId, List.of())));
            assertEquals(ErrorCode.AI_INVALID_RESPONSE, error.getErrorCode());
        } finally { server.stop(0); }
    }

    @Test void configurationDoesNotExposeServiceToken() {
        var properties = new FastApiJevProperties("http://localhost:8085", "SENTINEL_TOKEN", 120);
        assertFalse(properties.toString().contains("SENTINEL_TOKEN"));
        assertTrue(properties.toString().contains("[REDACTED]"));
    }

    @ParameterizedTest
    @CsvSource({"verification,MISSING", "verification,NULL", "method,FACTUAL",
        "factual_consistency,VERIFIED", "confidence_basis,CALIBRATED",
        "confidence_basis,OFFLINE_STUB", "human_review,NOT_REQUIRED", "method,MISSING"})
    void rejectsMissingOrOverstatedVerification(String field, String value) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
            java.nio.file.Files.readString(java.nio.file.Path.of(
                "../../docs/ai/contracts/jev-response-example.json")));
        UUID requestId = UUID.randomUUID();
        payload.put("request_id", requestId.toString());
        if (field.equals("verification")) {
            if (value.equals("MISSING")) payload.remove(field);
            else payload.putNull(field);
        } else {
            var verification = (com.fasterxml.jackson.databind.node.ObjectNode) payload.get("verification");
            if (value.equals("MISSING")) verification.remove(field);
            else verification.put(field, value);
        }
        byte[] bytes = mapper.writeValueAsBytes(payload);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/jev", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client = new FastApiJevClient(new FastApiJevProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), "token", 5));
            var error = assertThrows(BusinessException.class, () -> client.analyze(
                new JevGateway.Request(requestId, List.of())));
            assertEquals(ErrorCode.AI_INVALID_RESPONSE, error.getErrorCode());
        } finally { server.stop(0); }
    }

    @Test void rejectsCredentialBearingEndpointWithoutEchoingIt() {
        var error = assertThrows(IllegalArgumentException.class, () ->
            new FastApiJevProperties("https://user:SENTINEL_TOKEN@example.com", "", 120));
        assertFalse(error.toString().contains("SENTINEL_TOKEN"));
        assertThrows(IllegalArgumentException.class, () ->
            new FastApiJevProperties("http://localhost:8085", "token", 0));
    }

    @Test void stubResponseRequiresExplicitOptIn() throws Exception {
        UUID requestId = UUID.randomUUID();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/jev", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = ("""
                {"request_id":"%s","submission_status":"ON_HOLD","regulations_status":"NOT_REGISTERED",
                "items":[{"item":"offline","judgment":"UNVERIFIABLE","evidence":[],"confidence":0,
                "verification_reason":"stub","supplement":"configure Gemini"}],
                "provider":"stub","model":"stub-v1","trace_status":"DISABLED","trace_id":null,
                "reason":"offline","input_tokens":0,"output_tokens":0,
                "prompt_version":"jev-v1","prompt_sha256":"%s","chain_version":"jev-chain-v1",
                "verification":{"method":"STRUCTURAL","factual_consistency":"NOT_VERIFIED",
                "confidence_basis":"OFFLINE_STUB","human_review":"REQUIRED"}}
                """).formatted(requestId,"a".repeat(64)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            var request = new JevGateway.Request(requestId, List.of());
            var normal = new FastApiJevClient(new FastApiJevProperties(url, "token", 5));
            assertThrows(BusinessException.class, () -> normal.analyze(request));
            var offline = new FastApiJevClient(new FastApiJevProperties(url, "token", 5, true));
            assertEquals("stub", offline.analyze(request).provider());
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @CsvSource({"401,AI_SERVICE_UNAVAILABLE", "403,AI_SERVICE_UNAVAILABLE", "429,AI_RATE_LIMITED",
        "503,AI_SERVICE_UNAVAILABLE", "400,INVALID_INPUT", "422,INVALID_INPUT", "415,INVALID_INPUT",
        "413,PAYLOAD_TOO_LARGE", "500,AI_UPSTREAM_ERROR", "502,AI_UPSTREAM_ERROR", "504,AI_TIMEOUT"})
    void mapsRemoteStatusWithoutLeakingBody(int status, ErrorCode expected) throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/jev", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes="SENTINEL_PATIENT_REMOTE_ERROR".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client=new FastApiJevClient(new FastApiJevProperties(
                "http://127.0.0.1:"+server.getAddress().getPort(),"test-token",5));
            var error=assertThrows(BusinessException.class, () -> client.analyze(
                new JevGateway.Request(UUID.randomUUID(),List.of())));
            assertEquals(expected,error.getErrorCode());
            assertFalse(error.getMessage().contains("SENTINEL"));
            assertNull(error.getCause());
        } finally { server.stop(0); }
    }

    @Test void malformedSuccessfulResponseIsBadGateway() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/jev", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes="SENTINEL_PATIENT_NOT_JSON".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client=new FastApiJevClient(new FastApiJevProperties(
                "http://127.0.0.1:"+server.getAddress().getPort(),"token",5));
            var error=assertThrows(BusinessException.class, () -> client.analyze(
                new JevGateway.Request(UUID.randomUUID(),List.of())));
            assertEquals(ErrorCode.AI_INVALID_RESPONSE,error.getErrorCode());
            assertFalse(error.getMessage().contains("SENTINEL"));
        } finally { server.stop(0); }
    }

    @Test void actualReadTimeoutIsClassified() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/jev", exchange -> {
            exchange.getRequestBody().readAllBytes();
            try { Thread.sleep(1600); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            var client=new FastApiJevClient(new FastApiJevProperties(
                "http://127.0.0.1:"+server.getAddress().getPort(),"token",1));
            var error=assertThrows(BusinessException.class, () -> client.analyze(
                new JevGateway.Request(UUID.randomUUID(),List.of())));
            assertEquals(ErrorCode.AI_TIMEOUT,error.getErrorCode());
        } finally { server.stop(0); }
    }
}
