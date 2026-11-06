package com.example.logistics.catalog.domain;

import com.example.logistics.shared.persistence.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity
@Table(name = "master_data_record")
public class MasterDataRecordEntity extends BaseAuditedEntity {
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "organization_id", nullable = false, length = 36) private UUID organizationId;
    @Column(name = "resource_type", nullable = false, length = 40) private String resourceType;
    @Column(nullable = false, length = 100) private String code;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, length = 40) private String status = "ACTIVE";
    @Column(name = "attributes_json", nullable = false, columnDefinition = "longtext") private String attributesJson = "{}";
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID value) { organizationId = value; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String value) { resourceType = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getAttributesJson() { return attributesJson; }
    public void setAttributesJson(String value) { attributesJson = value; }
}
