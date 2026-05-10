package com.ppiyaki.notification.push;

public record PushSendResult(
        boolean success,
        boolean tokenInvalid,
        String errorMessage
) {

    public static PushSendResult ok() {
        return new PushSendResult(true, false, null);
    }

    public static PushSendResult tokenInvalid(final String message) {
        return new PushSendResult(false, true, message);
    }

    public static PushSendResult failed(final String message) {
        return new PushSendResult(false, false, message);
    }
}
