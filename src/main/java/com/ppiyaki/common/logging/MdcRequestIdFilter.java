package com.ppiyaki.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MdcRequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";

    // 영숫자 + dash + underscore, 1~128자. CRLF/제어문자/공백 차단해 로그·헤더 인젝션 회피.
    private static final Pattern ALLOWED_REQUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {
        final String headerRequestId = request.getHeader(REQUEST_ID_HEADER);
        final String requestId = isValidIncomingId(headerRequestId)
                ? headerRequestId
                : UUID.randomUUID().toString();
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private static boolean isValidIncomingId(final String candidate) {
        return candidate != null && ALLOWED_REQUEST_ID.matcher(candidate).matches();
    }
}
