package com.ppiyaki.chat.service;

import java.util.Objects;

public class SessionExpiredException extends RuntimeException {

    public SessionExpiredException(final Long sessionId) {
        super("세션이 만료되었습니다. 새 세션을 생성해주세요. sessionId: "
                + Objects.requireNonNull(sessionId, "sessionId must not be null"));
    }
}
