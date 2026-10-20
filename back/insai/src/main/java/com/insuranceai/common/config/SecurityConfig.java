package com.insuranceai.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 기존 업무 API의 임시 보안 설정.
 * JEV 경로는 별도 JevAuthenticationFilter에서 JWT와 현재 계정 상태를 검증한다.
 * 다른 업무 API의 통합 인증은 아직 이 설정에서 활성화하지 않았다.
 *
 * TODO: API Gateway 도입 후 게이트웨이가 검증한 JWT 기준으로 인가 처리
 *       (X-User-Id / X-User-Roles 헤더 신뢰 또는 oauth2ResourceServer + JWKS).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> {})
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.authorizeHttpRequests(auth -> auth
						.anyRequest().permitAll());
		return http.build();
	}
}
