package com.ppiyaki.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class WellbeingPingCooldownStoreTest {

    private static final long SENIOR_ID = 1L;
    private static final long CAREGIVER_ID = 2L;
    private static final String KEY = "wellbeing-ping:cooldown:1:2";

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private WellbeingPingCooldownStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        store = new WellbeingPingCooldownStore(redisTemplate);
    }

    @Test
    @DisplayName("setIfAbsent 성공 시 tryAcquire true")
    void tryAcquire_success() {
        given(valueOps.setIfAbsent(eq(KEY), eq("1"), any(Duration.class))).willReturn(true);

        assertThat(store.tryAcquire(SENIOR_ID, CAREGIVER_ID)).isTrue();
    }

    @Test
    @DisplayName("setIfAbsent 실패 시 tryAcquire false")
    void tryAcquire_fails_when_key_exists() {
        given(valueOps.setIfAbsent(eq(KEY), eq("1"), any(Duration.class))).willReturn(false);

        assertThat(store.tryAcquire(SENIOR_ID, CAREGIVER_ID)).isFalse();
    }

    @Test
    @DisplayName("getRetryAfterSeconds 가 TTL 양수면 Optional 반환")
    void getRetryAfterSeconds_returns_positive_ttl() {
        given(redisTemplate.getExpire(KEY)).willReturn(42L);

        assertThat(store.getRetryAfterSeconds(SENIOR_ID, CAREGIVER_ID)).contains(42L);
    }

    @Test
    @DisplayName("TTL 0 이하면 Optional.empty")
    void getRetryAfterSeconds_empty_when_no_ttl() {
        given(redisTemplate.getExpire(KEY)).willReturn(-1L);

        assertThat(store.getRetryAfterSeconds(SENIOR_ID, CAREGIVER_ID)).isEmpty();
    }
}
