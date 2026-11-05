package com.example.logistics.identity.domain;

import com.example.logistics.shared.persistence.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "permission", uniqueConstraints = @UniqueConstraint(name = "uk_permission_key", columnNames = "permission_key"))
public class PermissionEntity extends BaseAuditedEntity {
    @Column(name = "permission_key", nullable = false, length = 120) private String permissionKey;
    @Column(length = 500) private String description;
    @Column(name = "system_permission", nullable = false) private boolean systemPermission = true;
}
