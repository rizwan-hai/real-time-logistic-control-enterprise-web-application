package com.example.logistics.shared.config;

import com.example.logistics.notifications.V8__NotificationSchema;
import com.example.logistics.tracking.V7__TrackingSchema;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayJavaMigrationConfiguration {
    @Bean
    FlywayConfigurationCustomizer logisticsJavaMigrations() {
        return configuration -> configuration.javaMigrations(
                new V7__TrackingSchema(),
                new V8__NotificationSchema());
    }
}
