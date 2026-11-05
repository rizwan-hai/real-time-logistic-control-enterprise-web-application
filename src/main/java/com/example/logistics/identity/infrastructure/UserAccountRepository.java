package com.example.logistics.identity.infrastructure;

import com.example.logistics.identity.domain.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {
    List<UserAccountEntity> findAllByEmailNormalized(String email);
    List<UserAccountEntity> findAllByPhoneNormalized(String phone);
    Optional<UserAccountEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<UserAccountEntity> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    boolean existsByOrganizationIdAndEmailNormalized(UUID organizationId, String email);
    boolean existsByOrganizationIdAndPhoneNormalized(UUID organizationId, String phone);
}
