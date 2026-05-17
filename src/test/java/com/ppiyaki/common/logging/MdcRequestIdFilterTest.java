package com.ppiyaki.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
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

@DisplayName("MdcRequestIdFilter")
class MdcRequestIdFilterTest {

    private final MdcRequestIdFilter filter = new MdcRequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("X-Request-Id 헤더가 없으면 UUID를 생성해 MDC와 응답 헤더에 넣는다")
    void generates_uuid_when_header_missing() throws ServletException, IOException {
        // given
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final CapturingFilterChain chain = new CapturingFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(chain.requestIdSeen).isNotBlank();
        assertThat(response.getHeader(MdcRequestIdFilter.REQUEST_ID_HEADER))
                .isEqualTo(chain.requestIdSeen);
    }

    @Test
    @DisplayName("X-Request-Id 헤더가 있으면 그 값을 MDC와 응답 헤더에 그대로 사용한다")
    void honors_incoming_header() throws ServletException, IOException {
        // given
        final String inbound = "trace-abc-123";
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MdcRequestIdFilter.REQUEST_ID_HEADER, inbound);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final CapturingFilterChain chain = new CapturingFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(chain.requestIdSeen).isEqualTo(inbound);
        assertThat(response.getHeader(MdcRequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(inbound);
    }

    @Test
    @DisplayName("X-Request-Id 헤더에 제어문자가 있으면 무시하고 UUID로 폴백한다")
    void rejects_control_characters() throws ServletException, IOException {
        // given — CRLF 인젝션 시도 헤더
        final String malicious = "trace-abc\r\nInjected: yes";
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MdcRequestIdFilter.REQUEST_ID_HEADER, malicious);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final CapturingFilterChain chain = new CapturingFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then — 인입 헤더는 무시되고, 새 UUID가 생성되어야 함
        assertThat(chain.requestIdSeen).isNotEqualTo(malicious);
        assertThat(chain.requestIdSeen).matches("^[A-Za-z0-9-]{36}$"); // UUID 형식
        assertThat(response.getHeader(MdcRequestIdFilter.REQUEST_ID_HEADER))
                .doesNotContain("\r", "\n");
    }

    @Test
    @DisplayName("X-Request-Id 헤더가 128자 초과면 무시하고 UUID로 폴백한다")
    void rejects_overly_long_header() throws ServletException, IOException {
        // given
        final String tooLong = "a".repeat(129);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MdcRequestIdFilter.REQUEST_ID_HEADER, tooLong);
        final CapturingFilterChain chain = new CapturingFilterChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        assertThat(chain.requestIdSeen).isNotEqualTo(tooLong);
        assertThat(chain.requestIdSeen.length()).isLessThanOrEqualTo(128);
    }

    @Test
    @DisplayName("필터 종료 후 MDC가 정리되어 다음 요청에 누수되지 않는다")
    void clears_mdc_after_chain() throws ServletException, IOException {
        // given
        final MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(MDC.get(MdcRequestIdFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("필터 체인 내부에서 예외가 나도 MDC를 정리한다")
    void clears_mdc_when_chain_throws() {
        // given
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final FilterChain throwingChain = (req, res) -> {
            throw new ServletException("boom");
        };

        // when / then
        try {
            filter.doFilter(request, new MockHttpServletResponse(), throwingChain);
        } catch (final ServletException | IOException expected) {
            // pass
        }
        assertThat(MDC.get(MdcRequestIdFilter.MDC_REQUEST_ID)).isNull();
    }

    private static final class CapturingFilterChain implements FilterChain {

        private String requestIdSeen;

        @Override
        public void doFilter(final ServletRequest request, final ServletResponse response) {
            requestIdSeen = MDC.get(MdcRequestIdFilter.MDC_REQUEST_ID);
        }
    }
}
