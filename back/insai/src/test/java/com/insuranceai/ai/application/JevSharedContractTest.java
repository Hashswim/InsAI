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
        assertEquals(1,result.items().getFirst().evidence().getFirst().page());
        assertEquals(mapper.readTree(json),mapper.readTree(mapper.writeValueAsString(result)));
    }
}
