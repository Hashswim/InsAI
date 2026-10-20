package com.insuranceai.ai.infrastructure;

import com.insuranceai.ai.application.JevGateway;
import com.insuranceai.common.error.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FastApiJevClientTest {
    @Test void sendsAuthenticationAndParsesSnakeCase() throws Exception {
        UUID requestId=UUID.randomUUID(), traceId=UUID.randomUUID();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/jev", exchange -> {
            assertEquals("Bearer test-token",exchange.getRequestHeaders().getFirst("Authorization"));
            String request=new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(request.contains("request_id"));
            String response="""
                {"request_id":"%s","submission_status":"ON_HOLD","regulations_status":"NOT_REGISTERED",
                 "reason":"rules missing","items":[{"item":"date","judgment":"UNVERIFIABLE","evidence":[],
                 "confidence":0,"verification_reason":"unreadable","supplement":"rescan"}],
                 "provider":"gemini","model":"fake","prompt_version":"jev-v1","trace_id":"%s",
                 "trace_status":"RECORDED","input_tokens":10,"output_tokens":20}
                """.formatted(requestId,traceId);
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
            assertEquals("ON_HOLD",result.submissionStatus());
            assertEquals(10,result.inputTokens());
        } finally { server.stop(0); }
    }

    @Test void missingTokenFailsWithoutNetwork() {
        var client=new FastApiJevClient(new FastApiJevProperties("http://localhost:1","",1));
        assertThrows(BusinessException.class, () -> client.analyze(
            new JevGateway.Request(UUID.randomUUID(),List.of())));
    }
}
