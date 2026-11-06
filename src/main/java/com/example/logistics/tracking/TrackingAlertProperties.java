package com.example.logistics.tracking;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracking.alerts")
public record TrackingAlertProperties(
        double speedingKph,
        long idleMinutes,
        long deviceOfflineMinutes,
        double routeDeviationKm,
        long lateDeliveryRiskMinutes,
        long sweepIntervalMillis) {
    public TrackingAlertProperties {
        if (speedingKph <= 0 || idleMinutes <= 0 || deviceOfflineMinutes <= 0
                || routeDeviationKm <= 0 || lateDeliveryRiskMinutes < 0 || sweepIntervalMillis <= 0) {
            throw new IllegalArgumentException("Tracking alert thresholds must be positive");
        }
    }
}
