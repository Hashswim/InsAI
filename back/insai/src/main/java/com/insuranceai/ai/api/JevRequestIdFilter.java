package com.insuranceai.ai.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.common.web.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JevRequestIdFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    public JevRequestIdFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals("/ai/jev") && !path.startsWith("/ai/jev/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        UUID requestId = UUID.randomUUID();
        String supplied = request.getHeader("X-Request-ID");
        if (supplied != null) {
            try {
                UUID parsed = UUID.fromString(supplied);
                if (!parsed.toString().equalsIgnoreCase(supplied)) throw new IllegalArgumentException();
                requestId = parsed;
            } catch (IllegalArgumentException e) {
                response.setHeader("X-Request-ID", requestId.toString());
                response.setHeader("Cache-Control", "no-store");
                response.setStatus(400);
                response.setContentType("application/json");
                mapper.writeValue(response.getOutputStream(), ApiResponse.fail(
                    "INVALID_REQUEST_ID", "X-Request-ID must be a canonical UUID"));
                return;
            }
        }
        request.setAttribute("jevRequestId", requestId);
        response.setHeader("X-Request-ID", requestId.toString());
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }
}
