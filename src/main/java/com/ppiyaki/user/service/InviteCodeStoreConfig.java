package com.ppiyaki.user.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class InviteCodeStoreConfig {

    @Bean
    @Primary
    @ConditionalOnBean(StringRedisTemplate.class)
    public InviteCodeStore redisInviteCodeStore(final StringRedisTemplate redisTemplate) {
        return new RedisInviteCodeStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(InviteCodeStore.class)
    public InviteCodeStore inMemoryInviteCodeStore() {
        return new InMemoryInviteCodeStore();
    }
}
