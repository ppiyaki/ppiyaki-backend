package com.ppiyaki.common.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "kakao.oidc")
public record KakaoOidcProperties(
        @NotBlank String issuer,
        @NotBlank String jwksUri,
        @NotBlank String audience
) {
}
