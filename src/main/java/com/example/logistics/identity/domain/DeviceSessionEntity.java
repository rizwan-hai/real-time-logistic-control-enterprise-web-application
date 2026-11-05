package com.example.logistics.identity.domain;

import com.example.logistics.shared.persistence.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "device_session", uniqueConstraints = @UniqueConstraint(name = "uk_device_session_family", columnNames = "family_id"), indexes = @Index(name = "ix_device_session_user_active", columnList = "user_id,revoked_at,expires_at"))
public class DeviceSessionEntity extends BaseAuditedEntity {
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "family_id", nullable = false, length = 36) private UUID familyId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "user_id", nullable = false, length = 36) private UUID userId;
    @Column(name = "device_name", nullable = false, length = 160) private String deviceName;
    @Column(name = "user_agent", length = 500) private String userAgent;
    @Column(name = "created_ip", length = 64) private String createdIp;
    @Column(name = "last_seen_at", nullable = false) private Instant lastSeenAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoked_reason", length = 80) private String revokedReason;
}
