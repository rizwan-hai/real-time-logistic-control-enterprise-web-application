package com.example.logistics.identity.infrastructure;

import com.example.logistics.identity.domain.UserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UUID> {
    List<UserRoleEntity> findAllByUserIdAndActiveTrue(UUID userId);
    List<UserRoleEntity> findAllByUserIdAndOrganizationIdAndActiveTrue(UUID userId, UUID organizationId);
    Optional<UserRoleEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<UserRoleEntity> findByUserIdAndRoleIdAndAssignmentScopeId(UUID userId, UUID roleId, UUID scopeId);
    void deleteAllByUserId(UUID userId);
}
