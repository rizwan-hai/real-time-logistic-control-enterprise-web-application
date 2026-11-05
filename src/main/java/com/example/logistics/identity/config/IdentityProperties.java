package com.example.logistics.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.identity")
public record IdentityProperties(String issuer, String audience, int accessTokenMinutes, int refreshTokenDays,
                                 int passwordResetMinutes, String appBaseUrl, String fromEmail,
                                 String jwtSecret, String devAdminEmail, String devAdminPassword, boolean secureCookies) {}
