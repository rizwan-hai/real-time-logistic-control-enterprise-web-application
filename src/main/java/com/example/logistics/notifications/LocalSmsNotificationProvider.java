package com.example.logistics.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
public class LocalSmsNotificationProvider implements NotificationChannelProvider {
    private static final Logger log = LoggerFactory.getLogger(LocalSmsNotificationProvider.class);

    @Override public String channel() { return "SMS"; }

    @Override public void deliver(NotificationDelivery delivery) {
        if (delivery.phone() == null || delivery.phone().isBlank()) {
            throw new IllegalStateException("SMS delivery is enabled but the user has no phone number");
        }
        log.info("Local SMS adapter accepted notification {}", delivery.notificationId());
    }
}
