package com.ppiyaki.common.auth;

import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@EnableConfigurationProperties(KakaoOAuthProperties.class)
public class KakaoOAuthClient {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final KakaoOAuthProperties properties;
    private final RestClient restClient;

    public KakaoOAuthClient(final KakaoOAuthProperties properties, final RestClient.Builder restClientBuilder) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.restClient = restClientBuilder.build();
    }

    public String fetchAccessToken(final String code, final String redirectUri) {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(redirectUri, "redirectUri must not be null");

        final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", properties.clientId());
        body.add("client_secret", properties.clientSecret());
        body.add("redirect_uri", redirectUri);
        body.add("code", code);

        @SuppressWarnings("unchecked") final Map<String, Object> response = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(Map.class);

        return (String) response.get("access_token");
    }

    @SuppressWarnings("unchecked")
    public KakaoUserInfo fetchUserInfo(final String accessToken) {
        Objects.requireNonNull(accessToken, "accessToken must not be null");

        final Map<String, Object> response = restClient.get()
                .uri(USER_INFO_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);

        final Long kakaoId = ((Number) response.get("id")).longValue();
        final Map<String, Object> kakaoAccount = (Map<String, Object>) response.get("kakao_account");
        final Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
        final String nickname = (String) profile.get("nickname");

        return new KakaoUserInfo(kakaoId, nickname);
    }

    public record KakaoUserInfo(
            Long id,
            String nickname
    ) {
    }
}
