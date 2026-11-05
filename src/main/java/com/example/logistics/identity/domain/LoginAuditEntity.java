package com.example.logistics.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
@Entity @Immutable @Table(name = "login_audit", indexes = {
        @Index(name = "ix_login_audit_user_time", columnList = "user_id,occurred_at"),
        @Index(name = "ix_login_audit_fingerprint_time", columnList = "identifier_fingerprint,occurred_at"),
        @Index(name = "ix_login_audit_org_time", columnList = "organization_id,occurred_at")})
public class LoginAuditEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36, nullable = false, updatable = false) private UUID id = UUID.randomUUID();
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "user_id", length = 36, updatable = false) private UUID userId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "organization_id", length = 36, updatable = false) private UUID organizationId;
    @Column(name = "identifier_fingerprint", length = 64, nullable = false, updatable = false) private String identifierFingerprint;
    @Column(nullable = false, length = 32, updatable = false) private String outcome;
    @Column(name = "failure_code", length = 80, updatable = false) private String failureCode;
    @Column(name = "ip_address", length = 64, updatable = false) private String ipAddress;
    @Column(name = "user_agent", length = 500, updatable = false) private String userAgent;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    @Column(name = "correlation_id", length = 64, updatable = false) private String correlationId;
}
