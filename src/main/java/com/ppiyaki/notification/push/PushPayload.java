package com.ppiyaki.notification.push;

import java.util.Map;

public record PushPayload(
        String title,
        String body,
        Map<String, String> data
) {
}
