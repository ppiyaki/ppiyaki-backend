package com.ppiyaki.chat.service;

import java.util.Objects;

public class SessionAccessDeniedException extends RuntimeException {

    public SessionAccessDeniedException(final Long sessionId) {
        super("세션에 대한 접근 권한이 없습니다. sessionId: "
                + Objects.requireNonNull(sessionId, "sessionId must not be null"));
    }
}
