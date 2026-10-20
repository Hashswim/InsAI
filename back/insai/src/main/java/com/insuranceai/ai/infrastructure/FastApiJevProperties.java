package com.insuranceai.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("insai.jev")
public record FastApiJevProperties(
    @DefaultValue("http://localhost:8085") String baseUrl,
    @DefaultValue("") String serviceToken,
    @DefaultValue("120") int timeoutSeconds
) {}
