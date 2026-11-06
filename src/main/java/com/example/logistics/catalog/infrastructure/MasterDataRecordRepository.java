package com.example.logistics.catalog.infrastructure;

import com.example.logistics.catalog.domain.MasterDataRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

public interface MasterDataRecordRepository extends JpaRepository<MasterDataRecordEntity, UUID>, JpaSpecificationExecutor<MasterDataRecordEntity> {
    Optional<MasterDataRecordEntity> findByIdAndOrganizationIdAndResourceType(UUID id, UUID organizationId, String resourceType);
    boolean existsByOrganizationIdAndResourceTypeAndCodeIgnoreCase(UUID organizationId, String resourceType, String code);
}
