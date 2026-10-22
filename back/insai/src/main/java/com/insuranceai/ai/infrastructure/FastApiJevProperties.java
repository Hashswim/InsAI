package com.insuranceai.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import java.net.URI;

@ConfigurationProperties("insai.jev")
public record FastApiJevProperties(
    @DefaultValue("http://localhost:8085") String baseUrl,
    @DefaultValue("") String serviceToken,
    @DefaultValue("120") int timeoutSeconds,
    @DefaultValue("false") boolean allowStub
) {
    @ConstructorBinding
    public FastApiJevProperties {
        try {
            URI uri = URI.create(baseUrl);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || timeoutSeconds < 1 || timeoutSeconds > 300) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid JEV endpoint or timeout configuration");
        }
        serviceToken = serviceToken == null ? "" : serviceToken;
        if (serviceToken.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("Invalid JEV service token format");
        }
    }

    public FastApiJevProperties(String baseUrl, String serviceToken, int timeoutSeconds) {
        this(baseUrl, serviceToken, timeoutSeconds, false);
    }

    @Override
    public String toString() {
        return "FastApiJevProperties[baseUrl=" + baseUrl + ", serviceToken=[REDACTED], timeoutSeconds="
            + timeoutSeconds + ", allowStub=" + allowStub + "]";
    }
}
