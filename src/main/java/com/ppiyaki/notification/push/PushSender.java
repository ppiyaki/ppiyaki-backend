package com.ppiyaki.notification.push;

public interface PushSender {

    PushSendResult send(final String deviceToken, final PushPayload payload);
}
