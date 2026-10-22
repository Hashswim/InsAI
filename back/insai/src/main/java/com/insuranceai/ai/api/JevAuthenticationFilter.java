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
            if (actor.claimScoped() && !claimTokenAllowed(request, actor)) throw new BusinessException(ErrorCode.FORBIDDEN);
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

    /**
     * 청구 단위 토큰은 케이스가 드러나는 경로에서만 쓴다. 경로의 케이스는 여기서, 본문의 caseId는 컨트롤러에서 비교한다.
     * 분석·작업 ID로 조회하는 경로는 케이스를 알 수 없으므로 로그인 토큰이 필요하다.
     */
    private static boolean claimTokenAllowed(HttpServletRequest request, AuthenticateAccessTokenService.Actor actor) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/ai/jev/cases/")) {
            String caseSegment = path.substring("/ai/jev/cases/".length()).split("/", 2)[0];
            return caseSegment.equals(actor.caseId().toString());
        }
        return "POST".equals(request.getMethod()) && (path.equals("/ai/jev") || path.equals("/ai/jev/jobs"));
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
