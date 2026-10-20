package com.insuranceai.ai.infrastructure;

import com.insuranceai.ai.application.*;
import com.insuranceai.common.error.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
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
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "JEV 서비스 인증 설정이 필요합니다.");
        }
        try {
            JevResult result = client.post().uri("/internal/jev")
                .header("Authorization", "Bearer " + properties.serviceToken())
                .body(request).retrieve().body(JevResult.class);
            if (result == null || !request.requestId().equals(result.requestId())
                || !"ON_HOLD".equals(result.submissionStatus())
                || !"NOT_REGISTERED".equals(result.regulationsStatus())
                || result.items() == null || result.items().isEmpty()
                || result.model() == null || !"gemini".equals(result.provider())) {
                throw new IllegalStateException();
            }
            return result;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 분석 서비스 호출에 실패했습니다.");
        }
    }
}
