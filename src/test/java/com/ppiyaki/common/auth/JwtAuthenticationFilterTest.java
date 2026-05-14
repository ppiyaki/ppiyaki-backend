package com.ppiyaki.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";

    private final JwtProvider jwtProvider = new JwtProvider(
            new JwtProperties(TEST_SECRET, 1800000L, 1209600000L));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("role claim이 있는 토큰 → ROLE_<role> 권한이 부착된 Authentication이 생성된다")
    void token_with_role_attaches_authority() throws ServletException, IOException {
        // given
        final Long userId = 100L;
        final String token = jwtProvider.createAccessToken(userId, "CAREGIVER");
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(userId);
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_CAREGIVER");
    }

    @Test
    @DisplayName("role claim이 없는 deprecated 토큰 → 빈 권한으로 인증되어 하위 호환된다")
    void token_without_role_keeps_empty_authorities() throws ServletException, IOException {
        // given
        @SuppressWarnings("deprecation") final String token = jwtProvider.createAccessToken(200L);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(200L);
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("Authorization 헤더 없음 → SecurityContext가 비어 있다")
    void no_authorization_header_leaves_context_empty() throws ServletException, IOException {
        // given
        final MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("invalid 토큰 → SecurityContext가 비어 있다")
    void invalid_token_leaves_context_empty() throws ServletException, IOException {
        // given
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.token.value");

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("유효 토큰 → 체인 실행 중엔 MDC userId가 채워지고 종료 후엔 비워진다")
    void valid_token_sets_user_mdc_during_chain_and_clears_after() throws ServletException, IOException {
        // given
        final Long userId = 333L;
        final String token = jwtProvider.createAccessToken(userId, "SENIOR");
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        final UserIdCapturingChain chain = new UserIdCapturingChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        assertThat(chain.userIdInsideChain).isEqualTo("333");
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    @DisplayName("토큰 없음 → MDC userId가 채워지지 않고 체인 후에도 null")
    void no_token_leaves_user_mdc_null() throws ServletException, IOException {
        // given
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final UserIdCapturingChain chain = new UserIdCapturingChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        assertThat(chain.userIdInsideChain).isNull();
        assertThat(MDC.get("userId")).isNull();
    }

    private static final class UserIdCapturingChain implements jakarta.servlet.FilterChain {

        private String userIdInsideChain;

        @Override
        public void doFilter(final ServletRequest request, final ServletResponse response) {
            userIdInsideChain = MDC.get("userId");
        }
    }
}
