package com.insuranceai.ai.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.auth.application.AuthenticateAccessTokenService;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.common.web.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class JevAuthenticationFilter extends OncePerRequestFilter {
    private final AuthenticateAccessTokenService authentication;
    private final ObjectMapper mapper;
    public JevAuthenticationFilter(AuthenticateAccessTokenService authentication, ObjectMapper mapper) {
        this.authentication = authentication;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals("/ai/jev") && !path.startsWith("/ai/jev/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        try {
            var values = Collections.list(request.getHeaders("Authorization"));
            if (values.size() != 1) throw new BusinessException(ErrorCode.UNAUTHORIZED);
            String header = values.getFirst();
            if (header.length() > 8192) throw new BusinessException(ErrorCode.UNAUTHORIZED);
            String[] parts = header.split(" ", -1);
            if (parts.length != 2 || !parts[0].equalsIgnoreCase("Bearer") || parts[1].isBlank()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            var actor = authentication.authenticate(parts[1]);
            if (!Set.of("ROLE_CLAIMANT", "ROLE_ADJUSTER").contains(actor.authority())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            request.setAttribute("jevActor", actor);
        } catch (BusinessException e) {
            reject(response, e.getErrorCode());
            return;
        } catch (RuntimeException e) {
            reject(response, ErrorCode.AI_SERVICE_UNAVAILABLE);
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setHeader("Cache-Control", "no-store");
        if (code == ErrorCode.UNAUTHORIZED) response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiResponse.fail(code.name(), code.defaultMessage()));
    }
}
