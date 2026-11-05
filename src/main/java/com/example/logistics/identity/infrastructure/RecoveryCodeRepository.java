package com.example.logistics.identity.infrastructure;

import com.example.logistics.identity.domain.RecoveryCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCodeEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RecoveryCodeEntity c where c.userId = :userId and c.codeHash = :codeHash and c.usedAt is null")
    Optional<RecoveryCodeEntity> findByUserIdAndCodeHashAndUsedAtIsNull(@Param("userId") UUID userId, @Param("codeHash") String codeHash);
    void deleteAllByUserId(UUID userId);
}
