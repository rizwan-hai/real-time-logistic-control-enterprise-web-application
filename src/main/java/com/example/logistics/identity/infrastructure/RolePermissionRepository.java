package com.example.logistics.identity.infrastructure;

import com.example.logistics.identity.domain.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, UUID> {
    List<RolePermissionEntity> findAllByRoleId(UUID roleId);
    List<RolePermissionEntity> findAllByRoleIdIn(List<UUID> roleIds);
    Optional<RolePermissionEntity> findByRoleIdAndPermissionId(UUID roleId, UUID permissionId);
    void deleteAllByRoleId(UUID roleId);
}
