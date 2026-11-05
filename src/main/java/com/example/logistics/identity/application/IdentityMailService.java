package com.example.logistics.identity.application;

import com.example.logistics.identity.config.IdentityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class IdentityMailService {
    private static final Logger log = LoggerFactory.getLogger(IdentityMailService.class);
    private final JavaMailSender sender;
    private final IdentityProperties properties;
    public IdentityMailService(JavaMailSender sender, IdentityProperties properties) { this.sender = sender; this.properties = properties; }

    public void sendPasswordReset(String email, String rawToken) {
        String link = properties.appBaseUrl() + "/reset?token=" + java.net.URLEncoder.encode(rawToken, java.nio.charset.StandardCharsets.UTF_8);
        var message = new SimpleMailMessage(); message.setFrom(properties.fromEmail()); message.setTo(email);
        message.setSubject("Reset your logistics platform password");
        message.setText("Use this one-time link within " + properties.passwordResetMinutes() + " minutes:\n\n" + link);
        try { sender.send(message); }
        catch (RuntimeException ex) { log.warn("Password reset email delivery failed"); }
    }
}
