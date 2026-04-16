package com.ppiyaki.common.ai;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "openai")
@ConditionalOnProperty(prefix = "openai", name = "api-key")
public record OpenAiProperties(
        @NotBlank String apiKey,
        String model
) {
}
