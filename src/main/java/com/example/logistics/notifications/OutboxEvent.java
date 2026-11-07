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
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36) private UUID id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "organization_id", length = 36) private UUID organizationId;
    @Column(name = "event_type", nullable = false, length = 200) private String eventType;
    @Column(name = "schema_version", nullable = false) private int schemaVersion;
    @Column(name = "aggregate_type", nullable = false, length = 100) private String aggregateType;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "aggregate_id", nullable = false, length = 36) private UUID aggregateId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(nullable = false) private int attempts;
    @Column(name = "last_error", length = 1000) private String lastError;

    protected OutboxEvent() {}
}
