package com.insuranceai.ai.infrastructure;

import com.insuranceai.ai.application.*;
import com.insuranceai.common.error.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.http.MediaType;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
@EnableConfigurationProperties(FastApiJevProperties.class)
public class FastApiJevClient implements JevGateway {
    private final RestClient client;
    private final FastApiJevProperties properties;
    static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final JevWireResponseDecoder decoder = new JevWireResponseDecoder();

    public FastApiJevClient(FastApiJevProperties properties) {
        this.properties = properties;
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }
    public JevResult analyze(Request request) {
        if (properties.serviceToken().isBlank()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
        long started = System.nanoTime();
        try {
            return client.post().uri("/internal/jev")
                .header("Authorization", "Bearer " + properties.serviceToken())
                .header("X-Request-ID", request.requestId().toString())
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(request).exchange((sent, response) -> {
                    // Close the stream ourselves before Spring's response.close() can drain it.
                    try (var body = response.getBody()) {
                        int status = response.getStatusCode().value();
                        if (status != 200) throw new BusinessException(mapStatus(status));
                        var headers = response.getHeaders();
                        var type = headers.getContentType();
                        if (headers.getOrEmpty("Content-Type").size() != 1
                            || headers.getOrEmpty("Content-Encoding").size() > 1
                            || type == null || !"application".equals(type.getType()) || !"json".equals(type.getSubtype())
                            || (type.getCharset() != null && !java.nio.charset.StandardCharsets.UTF_8.equals(type.getCharset()))
                            || (headers.getFirst("Content-Encoding") != null
                                && !"identity".equalsIgnoreCase(headers.getFirst("Content-Encoding")))
                            || headers.getContentLength() > MAX_RESPONSE_BYTES) {
                            throw new BusinessException(ErrorCode.AI_INVALID_RESPONSE);
                        }
                        byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (bytes.length > MAX_RESPONSE_BYTES) throw new BusinessException(ErrorCode.AI_INVALID_RESPONSE);
                        try {
                            return decoder.decode(bytes, request, properties.allowStub());
                        } catch (java.io.IOException | IllegalArgumentException e) {
                            throw new BusinessException(ErrorCode.AI_INVALID_RESPONSE);
                        }
                    }
                });
        } catch (BusinessException e) {
            throw e;
        } catch (ResourceAccessException e) {
            // Spring closes a timed-out streaming body with an ordinary IOException.
            boolean deadlineReached = System.nanoTime() - started >= Duration.ofSeconds(properties.timeoutSeconds()).toNanos();
            throw new BusinessException(isTimeout(e) || deadlineReached ? ErrorCode.AI_TIMEOUT : ErrorCode.AI_SERVICE_UNAVAILABLE);
        } catch (RestClientException | IllegalStateException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AI_INVALID_RESPONSE);
        }
    }

    private static ErrorCode mapStatus(int status) {
        return switch (status) {
            case 400, 415, 422 -> ErrorCode.INVALID_INPUT;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 401, 403, 503 -> ErrorCode.AI_SERVICE_UNAVAILABLE;
            case 429 -> ErrorCode.AI_RATE_LIMITED;
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
