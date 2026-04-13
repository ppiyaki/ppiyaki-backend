package com.ppiyaki.chat.service;

public class SessionAccessDeniedException extends RuntimeException {

    public SessionAccessDeniedException(final Long sessionId) {
        super("세션에 대한 접근 권한이 없습니다. sessionId: " + sessionId);
    }
}
