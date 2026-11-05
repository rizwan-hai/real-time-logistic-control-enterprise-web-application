package com.example.logistics.identity.infrastructure;

import com.example.logistics.identity.domain.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<PermissionEntity, UUID> {
    Optional<PermissionEntity> findByPermissionKey(String permissionKey);
    List<PermissionEntity> findAllByOrderByPermissionKey();
}
