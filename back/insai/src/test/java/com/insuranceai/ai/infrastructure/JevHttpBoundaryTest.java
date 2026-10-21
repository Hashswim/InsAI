package com.insuranceai.ai.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.insuranceai.ai.application.JevGateway;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class JevHttpBoundaryTest {
    @ParameterizedTest
    @ValueSource(strings = {"valid", "wrong_mime", "missing_mime", "wrong_charset", "encoding", "duplicate_mime",
        "declared_oversize", "chunked_oversize", "redirect", "created", "invalid_utf8", "utf16", "truncated"})
    void boundsTransportAndNeverReplaysPost(String mode) throws Exception {
        var request = new JevGateway.Request(UUID.randomUUID(), List.of(
            new JevGateway.Document(UUID.fromString("22222222-2222-4222-8222-222222222222"), "DIAGNOSIS", "synthetic")));
        var mapper = new ObjectMapper();
        var root = (ObjectNode) mapper.readTree(Files.readString(Path.of("../../docs/ai/contracts/jev-response-example.json")));
        root.put("request_id", request.requestId().toString());
        byte[] valid = mapper.writeValueAsBytes(root);
        var calls = new AtomicInteger();
        var redirects = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirected", exchange -> { redirects.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.createContext("/internal/jev", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            var headers = exchange.getResponseHeaders();
            if (!mode.equals("missing_mime")) headers.set("Content-Type", "application/json");
            if (mode.equals("wrong_mime")) headers.set("Content-Type", "text/plain");
            if (mode.equals("wrong_charset")) headers.set("Content-Type", "application/json;charset=ISO-8859-1");
            if (mode.equals("encoding")) headers.set("Content-Encoding", "gzip");
            if (mode.equals("duplicate_mime")) headers.add("Content-Type", "text/plain");
            if (mode.equals("redirect")) headers.set("Location", "/redirected");
            byte[] body = switch (mode) {
                case "chunked_oversize" -> new byte[FastApiJevClient.MAX_RESPONSE_BYTES + 1];
                case "invalid_utf8" -> new byte[] {(byte) 0xc3, (byte) 0x28};
                case "utf16" -> root.toString().getBytes(StandardCharsets.UTF_16);
                case "truncated" -> "{\"reason\":\"SENTINEL_PATIENT".getBytes(StandardCharsets.UTF_8);
                default -> valid;
            };
            int status = mode.equals("redirect") ? 307 : mode.equals("created") ? 201 : 200;
            long length = mode.equals("declared_oversize") ? FastApiJevClient.MAX_RESPONSE_BYTES + 1L
                : mode.equals("chunked_oversize") ? 0 : body.length;
            exchange.sendResponseHeaders(status, length);
            try { exchange.getResponseBody().write(body); }
            catch (java.io.IOException ignored) { /* client deliberately closes oversized responses */ }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var client = new FastApiJevClient(new FastApiJevProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), "SENTINEL_TOKEN", 5));
            if (mode.equals("valid")) assertEquals("gemini", client.analyze(request).provider());
            else {
                var error = assertThrows(BusinessException.class, () -> client.analyze(request));
                assertEquals(mode.equals("redirect") || mode.equals("created")
                    ? ErrorCode.AI_UPSTREAM_ERROR : ErrorCode.AI_INVALID_RESPONSE, error.getErrorCode());
                assertFalse(error.toString().contains("SENTINEL"));
                assertNull(error.getCause());
            }
            assertEquals(1, calls.get());
            assertEquals(0, redirects.get());
        } finally { server.stop(0); }
    }

    @Test void timeoutAlsoBoundsBodyAfterHeadersArrive() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new AtomicInteger();
        server.createContext("/internal/jev", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try { Thread.sleep(2200); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var client = new FastApiJevClient(new FastApiJevProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), "token", 1));
            long started = System.nanoTime();
            var error = assertThrows(BusinessException.class, () -> client.analyze(
                new JevGateway.Request(UUID.randomUUID(), List.of())));
            assertEquals(ErrorCode.AI_TIMEOUT, error.getErrorCode());
            assertTrue(System.nanoTime() - started < java.time.Duration.ofSeconds(2).toNanos());
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }
}
