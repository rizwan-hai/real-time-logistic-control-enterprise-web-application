package com.example.logistics.shared.config;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Session;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfiguration {
    @Bean
    public JavaMailSender javaMailSender() {
        return new JavaMailSenderImpl() {
            @Override
            public jakarta.mail.internet.MimeMessage createMimeMessage() {
                return new MimeMessage((Session) null);
            }

            @Override
            public jakarta.mail.internet.MimeMessage createMimeMessage(java.io.InputStream contentStream) {
                return new MimeMessage((Session) null);
            }

            @Override
            public void send(SimpleMailMessage simpleMessage) {
                // Development stub: local mail delivery is intentionally non-blocking.
            }

            @Override
            public void send(SimpleMailMessage... simpleMessages) {
                // Development stub: local mail delivery is intentionally non-blocking.
            }

            @Override
            public void send(jakarta.mail.internet.MimeMessage mimeMessage) {
                // Development stub: local mail delivery is intentionally non-blocking.
            }

            @Override
            public void send(jakarta.mail.internet.MimeMessage... mimeMessages) {
                // Development stub: local mail delivery is intentionally non-blocking.
            }

            @Override
            public void send(org.springframework.mail.javamail.MimeMessagePreparator mimeMessagePreparator) {
                // Development stub: local mail delivery is intentionally non-blocking.
            }

            @Override
            public void send(org.springframework.mail.javamail.MimeMessagePreparator... mimeMessagePreparators) {
                // Development stub: local mail delivery is intentionally non-blocking.
            }
        };
    }
}
