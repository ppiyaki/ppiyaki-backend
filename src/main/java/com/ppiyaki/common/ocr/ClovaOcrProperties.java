package com.ppiyaki.common.ocr;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clova.ocr")
@ConditionalOnProperty(prefix = "clova.ocr", name = "secret")
public record ClovaOcrProperties(
        @NotBlank String secret,
        @NotBlank String invokeUrl
) {
}
