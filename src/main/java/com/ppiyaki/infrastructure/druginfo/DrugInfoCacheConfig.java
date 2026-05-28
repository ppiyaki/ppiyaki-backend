package com.ppiyaki.infrastructure.druginfo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class DrugInfoCacheConfig {

    @Bean
    @Primary
    @ConditionalOnBean(StringRedisTemplate.class)
    public DrugInfoCache redisDrugInfoCache(
            final StringRedisTemplate redisTemplate,
            final ObjectMapper objectMapper
    ) {
        return new RedisDrugInfoCache(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(DrugInfoCache.class)
    public DrugInfoCache inMemoryDrugInfoCache() {
        return new InMemoryDrugInfoCache();
    }
}
