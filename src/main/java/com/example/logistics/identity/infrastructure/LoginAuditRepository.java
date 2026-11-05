package com.example.logistics.identity.infrastructure;

import com.example.logistics.identity.domain.LoginAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LoginAuditRepository extends JpaRepository<LoginAuditEntity, UUID> {}
