package com.example.logistics.shared.config;

import com.example.logistics.shared.web.RedisRateLimitFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RateLimitConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true")
    FilterRegistrationBean<RedisRateLimitFilter> redisRateLimitFilter(StringRedisTemplate redis, RateLimitProperties properties) {
        var registration = new FilterRegistrationBean<>(new RedisRateLimitFilter(redis, properties));
        registration.setOrder(2);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
