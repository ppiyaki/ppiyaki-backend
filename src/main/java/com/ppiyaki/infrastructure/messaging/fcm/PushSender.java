package com.ppiyaki.infrastructure.messaging.fcm;

public interface PushSender {

    PushSendResult send(final String deviceToken, final PushPayload payload);
}
