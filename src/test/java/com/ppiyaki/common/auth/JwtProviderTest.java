package com.ppiyaki.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";

    private final JwtProvider jwtProvider = new JwtProvider(
            new JwtProperties(TEST_SECRET, 1800000L, 1209600000L));

    @Nested
    @DisplayName("access token 발급")
    class CreateAccessToken {

        @Test
        @DisplayName("발급된 토큰에서 userId를 추출할 수 있다")
        void extractUserId() {
            // given
            final Long userId = 1L;

            // when
            final String token = jwtProvider.createAccessToken(userId);

            // then
            assertThat(jwtProvider.extractUserId(token)).isEqualTo(userId);
        }

        @Test
        @DisplayName("발급된 토큰은 유효하다")
        void isValid() {
            // given
            final String token = jwtProvider.createAccessToken(1L);

            // when & then
            assertThat(jwtProvider.isValid(token)).isTrue();
        }
    }

    @Nested
    @DisplayName("토큰 검증")
    class Validation {

        @Test
        @DisplayName("잘못된 토큰은 유효하지 않다")
        void invalidToken() {
            // given
            final String invalidToken = "invalid.token.value";

            // when & then
            assertThat(jwtProvider.isValid(invalidToken)).isFalse();
        }

        @Test
        @DisplayName("만료된 토큰은 유효하지 않다")
        void expiredToken() {
            // given
            final JwtProvider shortLivedProvider = new JwtProvider(
                    new JwtProperties(TEST_SECRET, -1000L, -1000L));
            final String token = shortLivedProvider.createAccessToken(1L);

            // when & then
            assertThat(jwtProvider.isValid(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("refresh token 발급")
    class CreateRefreshToken {

        @Test
        @DisplayName("발급된 refresh 토큰에서 userId를 추출할 수 있다")
        void extractUserId() {
            // given
            final Long userId = 42L;

            // when
            final String token = jwtProvider.createRefreshToken(userId);

            // then
            assertThat(jwtProvider.extractUserId(token)).isEqualTo(userId);
        }
    }
}
