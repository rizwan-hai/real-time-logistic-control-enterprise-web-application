package com.example.logistics.identity.infrastructure;

import com.example.logistics.identity.domain.PasswordResetTokenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetTokenEntity t where t.tokenHash = :hash")
    Optional<PasswordResetTokenEntity> findByTokenHashForUpdate(@Param("hash") String hash);
}
