package com.example.logistics.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, int requests, int windowSeconds) {}
