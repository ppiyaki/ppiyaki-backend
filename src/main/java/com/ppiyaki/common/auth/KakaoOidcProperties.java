package com.ppiyaki.common.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.oidc")
public record KakaoOidcProperties(
        String issuer,
        String jwksUri,
        String audience
) {
}
