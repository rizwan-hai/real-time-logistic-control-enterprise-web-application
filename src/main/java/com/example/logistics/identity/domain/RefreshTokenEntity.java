package com.example.logistics.identity.domain;

import com.example.logistics.shared.persistence.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Entity @Table(name = "refresh_token", uniqueConstraints = @UniqueConstraint(name = "uk_refresh_hash", columnNames = "token_hash"), indexes = {
        @Index(name = "ix_refresh_family_status", columnList = "family_id,status"), @Index(name = "ix_refresh_user_expiry", columnList = "user_id,expires_at")})
public class RefreshTokenEntity extends BaseAuditedEntity {
    public enum Status { ACTIVE, ROTATED, REVOKED, EXPIRED }
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "family_id", nullable = false, length = 36) private UUID familyId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "device_session_id", nullable = false, length = 36) private UUID deviceSessionId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "user_id", nullable = false, length = 36) private UUID userId;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.ACTIVE;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "replaced_by_token_id", length = 36) private UUID replacedByTokenId;
    @Column(name = "created_ip", length = 64) private String createdIp;
}
