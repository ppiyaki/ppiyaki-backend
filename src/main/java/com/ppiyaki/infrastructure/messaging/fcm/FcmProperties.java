package com.ppiyaki.infrastructure.messaging.fcm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fcm")
public record FcmProperties(
        String projectId,
        String credentialsJsonBase64
) {

    public boolean isEnabled() {
        return projectId != null && !projectId.isBlank()
                && credentialsJsonBase64 != null && !credentialsJsonBase64.isBlank();
    }
}
