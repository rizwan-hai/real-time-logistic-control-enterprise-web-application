package com.example.logistics.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
@Entity @Immutable @EntityListeners(AuditingEntityListener.class)
@Table(name = "password_history", indexes = @Index(name = "ix_password_history_user_time", columnList = "user_id,created_at"))
public class PasswordHistoryEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36, nullable = false, updatable = false) private UUID id = UUID.randomUUID();
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "user_id", length = 36, nullable = false, updatable = false) private UUID userId;
    @Column(name = "password_hash", length = 100, nullable = false, updatable = false) private String passwordHash;
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    public PasswordHistoryEntity(UUID userId, String passwordHash) { this.userId = userId; this.passwordHash = passwordHash; }
}
