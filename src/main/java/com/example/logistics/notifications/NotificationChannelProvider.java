package com.example.logistics.notifications;

public interface NotificationChannelProvider {
    String channel();
    void deliver(NotificationDelivery delivery);
}
