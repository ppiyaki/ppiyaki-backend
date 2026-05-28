package com.ppiyaki.infrastructure.mfds;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class MfdsResponseCacheConfig {

    @Bean
    @Primary
    @ConditionalOnBean(StringRedisTemplate.class)
    public MfdsResponseCache redisMfdsResponseCache(
            final StringRedisTemplate redisTemplate,
            final ObjectMapper objectMapper
    ) {
        return new RedisMfdsResponseCache(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(MfdsResponseCache.class)
    public MfdsResponseCache inMemoryMfdsResponseCache() {
        return new InMemoryMfdsResponseCache();
    }
}
