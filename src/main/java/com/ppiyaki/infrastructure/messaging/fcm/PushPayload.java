package com.ppiyaki.infrastructure.messaging.fcm;

import java.util.Map;

public record PushPayload(
        String title,
        String body,
        Map<String, String> data
) {
}
