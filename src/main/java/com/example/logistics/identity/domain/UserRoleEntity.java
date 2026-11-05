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
@Entity @Table(name = "user_role", uniqueConstraints = @UniqueConstraint(name = "uk_user_role_assignment", columnNames = {"user_id", "role_id", "assignment_scope_id"}), indexes = {
        @Index(name = "ix_user_role_user_active", columnList = "user_id,active"), @Index(name = "ix_user_role_org", columnList = "organization_id,role_id")})
public class UserRoleEntity extends BaseAuditedEntity {
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "user_id", nullable = false, length = 36) private UUID userId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "role_id", nullable = false, length = 36) private UUID roleId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "organization_id", nullable = false, length = 36) private UUID organizationId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "warehouse_id", length = 36) private UUID warehouseId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "assignment_scope_id", nullable = false, length = 36) private UUID assignmentScopeId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "assigned_by", length = 36) private UUID assignedBy;
    @Column(nullable = false) private boolean active = true;
}
