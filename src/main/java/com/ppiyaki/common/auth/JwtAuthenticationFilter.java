package com.ppiyaki.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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

    private String extractToken(final HttpServletRequest request) {
        final String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
