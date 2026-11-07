package com.example.logistics.notifications;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationProvider implements NotificationChannelProvider {
    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotificationProvider(JavaMailSender mailSender,
                                     @Value("${app.identity.from-email:no-reply@logistics.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override public String channel() { return "EMAIL"; }

    @Override public void deliver(NotificationDelivery delivery) {
        if (delivery.email() == null || delivery.email().isBlank()) {
            throw new IllegalStateException("Email delivery is enabled but the user has no email address");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(delivery.email());
        message.setSubject(delivery.title());
        message.setText(delivery.body() + "\n\nOpen your notifications: " + delivery.deepLink());
        mailSender.send(message);
    }
}
