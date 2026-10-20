package com.insuranceai.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiPropertiesTest {
    @Test void configurationStringDoesNotExposeLegacyApiKey() {
        var properties = new AiProperties(true, "SENTINEL_API_KEY", "test-model");
        assertTrue(properties.usable());
        assertFalse(properties.toString().contains("SENTINEL_API_KEY"));
        assertTrue(properties.toString().contains("[REDACTED]"));
    }
}
