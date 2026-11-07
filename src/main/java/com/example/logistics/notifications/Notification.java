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
@Table(name = "notification")
public class Notification {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36) private UUID id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "organization_id", nullable = false, length = 36) private UUID organizationId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "user_id", nullable = false, length = 36) private UUID userId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "source_outbox_id", nullable = false, length = 36) private UUID sourceOutboxId;
    @Column(name = "event_fingerprint", nullable = false, length = 64) private String eventFingerprint;
    @Column(name = "event_type", nullable = false, length = 100) private String eventType;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 1000) private String body;
    @Column(nullable = false, length = 5) private String locale;
    @Column(name = "resource_type", nullable = false, length = 50) private String resourceType;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "resource_id", nullable = false, length = 36) private UUID resourceId;
    @Column(name = "deep_link", nullable = false, length = 300) private String deepLink;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "read_at") private Instant readAt;

    protected Notification() {}
}
