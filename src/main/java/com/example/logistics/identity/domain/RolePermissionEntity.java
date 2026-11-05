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
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "role_permission", uniqueConstraints = @UniqueConstraint(name = "uk_role_permission", columnNames = {"role_id", "permission_id"}), indexes = @Index(name = "ix_role_permission_role", columnList = "role_id"))
public class RolePermissionEntity extends BaseAuditedEntity {
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "role_id", nullable = false, length = 36) private UUID roleId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "permission_id", nullable = false, length = 36) private UUID permissionId;
}
