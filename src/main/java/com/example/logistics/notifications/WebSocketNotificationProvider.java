package com.example.logistics.notifications;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class WebSocketNotificationProvider implements NotificationChannelProvider {
    private final SimpMessagingTemplate messages;

    public WebSocketNotificationProvider(SimpMessagingTemplate messages) { this.messages = messages; }

    @Override public String channel() { return "WEBSOCKET"; }

    @Override public void deliver(NotificationDelivery delivery) {
        messages.convertAndSend("/topic/notifications/" + delivery.userId(), Map.of(
                "id", delivery.notificationId().toString(),
                "title", delivery.title(),
                "body", delivery.body(),
                "deepLink", delivery.deepLink()));
    }
}
