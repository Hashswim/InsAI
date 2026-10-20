package com.insuranceai.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JevSharedContractTest {
    @Test void sharedExampleRetainsFieldsAcrossJavaSerialization() throws Exception {
        var mapper=new ObjectMapper();
        var json=Files.readString(Path.of("../../docs/ai/contracts/jev-response-example.json"));
        var result=mapper.readValue(json,JevResult.class);
        assertEquals(UUID.fromString("11111111-1111-4111-8111-111111111111"), result.requestId());
        assertEquals("ON_HOLD", result.submissionStatus());
        assertEquals("RECORDED", result.traceStatus());
        assertEquals("jev-v1",result.promptVersion());
        assertEquals("jev-chain-v1",result.chainVersion());
        assertTrue(result.promptSha256().matches("[0-9a-f]{64}"));
        assertEquals(1,result.items().getFirst().evidence().getFirst().page());
        assertEquals(mapper.readTree(json),mapper.readTree(mapper.writeValueAsString(result)));
    }

    @Test void historicalResultsDoNotInventMissingReleaseIdentity() throws Exception {
        var mapper=new ObjectMapper();
        var result=mapper.readValue("{\"prompt_version\":\"jev-v1\"}",JevResult.class);
        assertEquals("jev-v1",result.promptVersion());
        assertNull(result.promptSha256());
        assertNull(result.chainVersion());
    }
}
