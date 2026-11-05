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
@Entity @Table(name = "user_account", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_org_email", columnNames = {"organization_id", "email_normalized"}),
        @UniqueConstraint(name = "uk_user_org_phone", columnNames = {"organization_id", "phone_normalized"})
}, indexes = @Index(name = "ix_user_org_active", columnList = "organization_id,active"))
public class UserAccountEntity extends BaseAuditedEntity {
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "organization_id", nullable = false, length = 36) private UUID organizationId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "customer_id", length = 36) private UUID customerId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "driver_id", length = 36) private UUID driverId;
    @Column(name = "email_normalized", length = 254) private String emailNormalized;
    @Column(name = "phone_normalized", length = 16) private String phoneNormalized;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "email_verified", nullable = false) private boolean emailVerified;
    @Column(name = "phone_verified", nullable = false) private boolean phoneVerified;
    @Column(name = "failed_login_attempts", nullable = false) private int failedLoginAttempts;
    @Column(name = "lock_level", nullable = false) private int lockLevel;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "totp_secret_encrypted", length = 512) private String totpSecretEncrypted;
    @Column(name = "totp_enabled", nullable = false) private boolean totpEnabled;
    @Column(name = "last_login_at") private Instant lastLoginAt;
}
