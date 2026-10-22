package com.insuranceai.ai.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.ai.api.JevAuthenticationFilter;
import com.insuranceai.auth.application.AuthenticateAccessTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JevSecurityConfig {
    @Bean
    public JevAuthenticationFilter jevAuthenticationFilter(
        AuthenticateAccessTokenService authentication, ObjectMapper mapper
    ) {
        return new JevAuthenticationFilter(authentication, mapper);
    }
}
