package com.example.logistics.notifications;

import org.springframework.stereotype.Component;

@Component
public class InAppNotificationProvider implements NotificationChannelProvider {
    @Override public String channel() { return "IN_APP"; }
    @Override public void deliver(NotificationDelivery delivery) { }
}
