package com.ppiyaki.chat.service;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(final Long sessionId) {
        super("세션을 찾을 수 없습니다: " + sessionId);
    }
}
