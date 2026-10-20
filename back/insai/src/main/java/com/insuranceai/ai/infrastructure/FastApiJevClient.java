package com.insuranceai.ai.infrastructure;

import com.insuranceai.ai.application.*;
import com.insuranceai.common.error.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.http.HttpStatusCode;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
@EnableConfigurationProperties(FastApiJevProperties.class)
public class FastApiJevClient implements JevGateway {
    private final RestClient client;
    private final FastApiJevProperties properties;

    public FastApiJevClient(FastApiJevProperties properties) {
        this.properties = properties;
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }
    public JevResult analyze(Request request) {
        if (properties.serviceToken().isBlank()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
        try {
            JevResult result = client.post().uri("/internal/jev")
                .header("Authorization", "Bearer " + properties.serviceToken())
                .header("X-Request-ID", request.requestId().toString())
                .body(request).retrieve()
                .onStatus(HttpStatusCode::isError, (sent, response) -> {
                    throw new BusinessException(mapStatus(response.getStatusCode().value()));
                }).body(JevResult.class);
            if (result == null || !request.requestId().equals(result.requestId())
                || !"ON_HOLD".equals(result.submissionStatus())
                || !"NOT_REGISTERED".equals(result.regulationsStatus())
                || result.items() == null || result.items().isEmpty()
                || result.model() == null
                || !("gemini".equals(result.provider())
                     || "openai".equals(result.provider())
                     || (properties.allowStub() && "stub".equals(result.provider())))) {
                throw new IllegalStateException();
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new BusinessException(isTimeout(e) ? ErrorCode.AI_TIMEOUT : ErrorCode.AI_SERVICE_UNAVAILABLE);
        } catch (RestClientException | IllegalStateException e) {
            throw new BusinessException(ErrorCode.AI_INVALID_RESPONSE);
        }
    }

    private static ErrorCode mapStatus(int status) {
        return switch (status) {
            case 400, 415, 422 -> ErrorCode.INVALID_INPUT;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 401, 403, 429, 503 -> ErrorCode.AI_SERVICE_UNAVAILABLE;
            case 504 -> ErrorCode.AI_TIMEOUT;
            default -> ErrorCode.AI_UPSTREAM_ERROR;
        };
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.http.HttpTimeoutException
                || cause instanceof java.net.SocketTimeoutException) return true;
        }
        return false;
    }
}
