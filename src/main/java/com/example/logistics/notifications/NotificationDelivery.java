package com.example.logistics.notifications;

import java.util.UUID;

public record NotificationDelivery(
        UUID notificationId,
        UUID userId,
        String title,
        String body,
        String deepLink,
        String email,
        String phone) {}
