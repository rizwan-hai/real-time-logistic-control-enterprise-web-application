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
@Table(name = "notification_template")
public class NotificationTemplate {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36) private UUID id;
    @Column(name = "event_type", nullable = false, length = 100) private String eventType;
    @Column(nullable = false, length = 5) private String locale;
    @Column(name = "title_template", nullable = false, length = 200) private String titleTemplate;
    @Column(name = "body_template", nullable = false, length = 1000) private String bodyTemplate;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected NotificationTemplate() {}
}
