package com.ppiyaki.common.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RateLimiterConfig {

    @Bean
    @Primary
    @ConditionalOnBean(StringRedisTemplate.class)
    public RateLimiter redisRateLimiter(final StringRedisTemplate redisTemplate) {
        return new RedisRateLimiter(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter inMemoryRateLimiter() {
        return new InMemoryRateLimiter();
    }

    @Bean
    @Primary
    @ConditionalOnBean(StringRedisTemplate.class)
    public AttemptLimiter redisAttemptLimiter(final StringRedisTemplate redisTemplate) {
        return new RedisAttemptLimiter(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(AttemptLimiter.class)
    public AttemptLimiter inMemoryAttemptLimiter() {
        return new InMemoryAttemptLimiter();
    }
}
