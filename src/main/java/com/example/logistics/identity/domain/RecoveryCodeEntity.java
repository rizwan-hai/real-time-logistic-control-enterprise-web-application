package com.example.logistics.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Entity @Table(name = "recovery_code", uniqueConstraints = @UniqueConstraint(name = "uk_recovery_user_hash", columnNames = {"user_id", "code_hash"}), indexes = @Index(name = "ix_recovery_user", columnList = "user_id,used_at"))
public class RecoveryCodeEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36, nullable = false, updatable = false) private UUID id = UUID.randomUUID();
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "user_id", length = 36, nullable = false, updatable = false) private UUID userId;
    @Column(name = "code_hash", length = 64, nullable = false, updatable = false) private String codeHash;
    @Column(name = "used_at") private Instant usedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    public RecoveryCodeEntity(UUID userId, String codeHash) { this.userId = userId; this.codeHash = codeHash; }
}
