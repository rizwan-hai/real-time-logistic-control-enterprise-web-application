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
@Table(name = "notification_delivery_log")
public class DeliveryLog {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36) private UUID id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "notification_id", nullable = false, length = 36) private UUID notificationId;
    @Column(nullable = false, length = 20) private String channel;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected DeliveryLog() {}
}
