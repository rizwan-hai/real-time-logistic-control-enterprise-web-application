package com.example.logistics.identity.infrastructure;

import com.example.logistics.identity.domain.DeviceSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceSessionRepository extends JpaRepository<DeviceSessionEntity, UUID> {
    Optional<DeviceSessionEntity> findByFamilyId(UUID familyId);
    List<DeviceSessionEntity> findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(UUID userId, java.time.Instant now);
    Optional<DeviceSessionEntity> findByIdAndUserId(UUID id, UUID userId);
}
