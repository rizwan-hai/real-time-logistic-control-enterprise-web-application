package com.example.logistics.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preference")
public class NotificationPreference {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36) private UUID id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "organization_id", nullable = false, length = 36) private UUID organizationId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "user_id", nullable = false, length = 36) private UUID userId;
    @Column(name = "in_app_enabled", nullable = false) private boolean inAppEnabled;
    @Column(name = "email_enabled", nullable = false) private boolean emailEnabled;
    @Column(name = "sms_enabled", nullable = false) private boolean smsEnabled;
    @Column(name = "web_push_enabled", nullable = false) private boolean webPushEnabled;
    @Column(nullable = false, length = 5) private String locale;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected NotificationPreference() {}
}
