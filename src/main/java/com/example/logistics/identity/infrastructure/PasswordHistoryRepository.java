package com.example.logistics.identity.infrastructure;

import com.example.logistics.identity.domain.PasswordHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistoryEntity, UUID> {
    List<PasswordHistoryEntity> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId);
    void deleteByUserIdAndIdNotIn(UUID userId, List<UUID> idsToKeep);
}
