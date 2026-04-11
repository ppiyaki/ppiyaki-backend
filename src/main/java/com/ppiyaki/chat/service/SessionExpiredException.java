package com.ppiyaki.chat.service;

public class SessionExpiredException extends RuntimeException {

    public SessionExpiredException(final Long sessionId) {
        super("세션이 만료되었습니다. 새 세션을 생성해주세요. sessionId: " + sessionId);
    }
}
