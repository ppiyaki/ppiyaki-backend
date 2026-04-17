package com.ppiyaki.common.druginfo;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "druginfo.api")
@ConditionalOnProperty(prefix = "druginfo.api", name = "service-key")
public record DrugInfoProperties(
        @NotBlank String serviceKey
) {
}
