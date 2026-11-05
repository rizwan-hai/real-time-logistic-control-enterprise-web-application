CREATE TABLE number_sequence (
    id CHAR(36) NOT NULL PRIMARY KEY,
    scope_id CHAR(36) NOT NULL,
    sequence_key VARCHAR(100) NOT NULL,
    prefix VARCHAR(30) NOT NULL DEFAULT '',
    current_value BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_number_sequence_scope_key UNIQUE (scope_id, sequence_key),
    CONSTRAINT chk_number_sequence_nonnegative CHECK (current_value >= 0)
);

CREATE TABLE setting (
    id CHAR(36) NOT NULL PRIMARY KEY,
    scope_id CHAR(36) NOT NULL,
    setting_key VARCHAR(150) NOT NULL,
    setting_value TEXT NOT NULL,
    value_type VARCHAR(30) NOT NULL DEFAULT 'STRING',
    is_secret BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_setting_scope_key UNIQUE (scope_id, setting_key)
);

CREATE TABLE audit_log (
    id CHAR(36) NOT NULL PRIMARY KEY,
    organization_id CHAR(36) NULL,
    actor_id CHAR(36) NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id CHAR(36) NULL,
    correlation_id VARCHAR(64) NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    change_summary TEXT NULL,
    INDEX ix_audit_org_time (organization_id, occurred_at),
    INDEX ix_audit_resource (resource_type, resource_id, occurred_at),
    INDEX ix_audit_actor_time (actor_id, occurred_at)
);

CREATE TABLE attachment (
    id CHAR(36) NOT NULL PRIMARY KEY,
    organization_id CHAR(36) NULL,
    owner_type VARCHAR(100) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    object_bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    detected_content_type VARCHAR(150) NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NULL,
    scan_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_attachment_object UNIQUE (object_bucket, object_key),
    CONSTRAINT chk_attachment_size_nonnegative CHECK (size_bytes >= 0),
    INDEX ix_attachment_owner (organization_id, owner_type, owner_id)
);

CREATE TABLE outbox_event (
    id CHAR(36) NOT NULL PRIMARY KEY,
    organization_id CHAR(36) NULL,
    event_type VARCHAR(200) NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id CHAR(36) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    payload TEXT NOT NULL,
    published_at TIMESTAMP(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    INDEX ix_outbox_pending (published_at, occurred_at),
    INDEX ix_outbox_aggregate (aggregate_type, aggregate_id, occurred_at),
    CONSTRAINT chk_outbox_attempts_nonnegative CHECK (attempts >= 0)
);
