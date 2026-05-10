package com.ppiyaki.notification.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoOpPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpPushSender.class);

    @Override
    public PushSendResult send(final String deviceToken, final PushPayload payload) {
        log.debug("FCM disabled — skip push (token={}, title={})", deviceToken, payload.title());
        return PushSendResult.ok();
    }
}
