package com.example.logistics.tracking;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration @EnableAsync @EnableScheduling @EnableConfigurationProperties(TrackingAlertProperties.class)
public class TrackingConfiguration {
    @Bean("trackingExecutor") public Executor trackingExecutor(){var e=new ThreadPoolTaskExecutor();e.setCorePoolSize(2);e.setMaxPoolSize(8);e.setQueueCapacity(10000);e.setThreadNamePrefix("gps-ingest-");e.initialize();return e;}
}
