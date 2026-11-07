package com.example.logistics.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
public class LocalWebPushNotificationProvider implements NotificationChannelProvider {
    private static final Logger log = LoggerFactory.getLogger(LocalWebPushNotificationProvider.class);

    @Override public String channel() { return "WEB_PUSH"; }

    @Override public void deliver(NotificationDelivery delivery) {
        log.info("Local Web Push adapter accepted notification {}", delivery.notificationId());
    }
}
