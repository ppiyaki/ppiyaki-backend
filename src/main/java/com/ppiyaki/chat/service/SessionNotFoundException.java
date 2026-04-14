package com.ppiyaki.chat.service;

import java.util.Objects;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(final Long sessionId) {
        super("세션을 찾을 수 없습니다: " + Objects.requireNonNull(sessionId, "sessionId must not be null"));
    }
}
