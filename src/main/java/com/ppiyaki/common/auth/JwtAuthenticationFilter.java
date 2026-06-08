package com.ppiyaki.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String MDC_USER_ID = "userId";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(final JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {
        final String token = extractToken(request);
        final boolean userIdSet = token != null && jwtProvider.isValid(token);

        // 토큰이 실렸는데 거부된 경우만 경로와 함께 기록 (사유는 JwtProvider 로그, requestId로 상관).
        // 토큰 미첨부(로그인 등 permitAll)는 정상이라 로깅하지 않음 — 노이즈 회피.
        if (token != null && !userIdSet) {
            log.warn("JWT 인증 실패 — {} {}", request.getMethod(), request.getRequestURI());
        }

        if (userIdSet) {
            final Long userId = jwtProvider.extractUserId(token);
            final String role = jwtProvider.extractRole(token);
            final List<GrantedAuthority> authorities = role == null
                    ? List.of()
                    : List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role));
            final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userId,
                    null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            MDC.put(MDC_USER_ID, userId.toString());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (userIdSet) {
                MDC.remove(MDC_USER_ID);
            }
        }
    }

    // SSE 등 비동기 응답은 스트림 종료/에러 시 컨테이너로 ASYNC/ERROR 디스패치가 한 번 더 일어난다.
    // OncePerRequestFilter는 기본적으로 두 디스패치를 건너뛰어 SecurityContext가 비는데,
    // Spring Security 6의 AuthorizationFilter는 모든 디스패치 타입에서 동작하므로 익명으로 간주돼 401(AUTH_001)이 난다.
    // 첫 이벤트 전송 전에 스트림이 실패하면 이 401이 클라이언트에 그대로 노출된다(REST는 재디스패치가 없어 무관).
    // 따라서 이 디스패치들에서도 필터가 토큰을 다시 읽어 컨텍스트를 복원하도록 한다.
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private String extractToken(final HttpServletRequest request) {
        final String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
