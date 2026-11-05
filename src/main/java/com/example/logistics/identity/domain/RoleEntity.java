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
@Entity @Table(name = "role", uniqueConstraints = @UniqueConstraint(name = "uk_role_org_key", columnNames = {"organization_id", "role_key"}), indexes = @Index(name = "ix_role_active", columnList = "active,role_key"))
public class RoleEntity extends BaseAuditedEntity {
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "organization_id", length = 36) private UUID organizationId;
    @Column(name = "role_key", nullable = false, length = 80) private String roleKey;
    @Column(name = "display_name", nullable = false, length = 120) private String displayName;
    @Column(length = 500) private String description;
    @Column(name = "system_role", nullable = false) private boolean systemRole;
    @Column(nullable = false) private boolean active = true;
}
