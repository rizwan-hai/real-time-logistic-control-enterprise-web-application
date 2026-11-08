package com.example.logistics.shared.config;

import com.example.logistics.notifications.V8__NotificationSchema;
import com.example.logistics.tracking.V7__TrackingSchema;
import com.example.logistics.analytics.V9__AnalyticsSchema;
import com.example.logistics.administration.V10__AdministrationSchema;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayJavaMigrationConfiguration {
    @Bean
    FlywayConfigurationCustomizer logisticsJavaMigrations() {
        return configuration -> configuration.javaMigrations(
                new V7__TrackingSchema(),
                new V8__NotificationSchema(),
                new V9__AnalyticsSchema(),
                new V10__AdministrationSchema());
    }
}
