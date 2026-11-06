CREATE TABLE master_data_record (
    id CHAR(36) NOT NULL PRIMARY KEY,
    organization_id CHAR(36) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    attributes_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_master_data_org_type_code UNIQUE (organization_id, resource_type, code),
    INDEX ix_master_data_org_type_name (organization_id, resource_type, name),
    INDEX ix_master_data_org_type_status (organization_id, resource_type, status)
);
