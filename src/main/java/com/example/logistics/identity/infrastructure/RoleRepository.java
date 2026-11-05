package com.example.logistics.identity.infrastructure;

import com.example.logistics.identity.domain.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findByRoleKeyAndOrganizationIdIsNull(String roleKey);
    Optional<RoleEntity> findByRoleKeyAndOrganizationId(String roleKey, UUID organizationId);
    Optional<RoleEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<RoleEntity> findAllByOrganizationIdIsNullOrOrganizationIdOrderByRoleKey(UUID organizationId);
}
