package com.ppiyaki.common.mfds;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "mfds.api")
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public record MfdsApiProperties(
        @NotBlank String serviceKey,
        @NotBlank String baseUrl,
        int connectTimeout,
        int readTimeout
) {
}
