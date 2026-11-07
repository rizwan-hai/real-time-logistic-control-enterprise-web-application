package com.example.logistics.notifications;

public final class NotificationSubscriptionAuthorization {
    private NotificationSubscriptionAuthorization() {}

    public static boolean allows(String authenticatedUserId, String destination) {
        return authenticatedUserId != null
                && destination != null
                && destination.equals("/topic/notifications/" + authenticatedUserId);
    }
}
