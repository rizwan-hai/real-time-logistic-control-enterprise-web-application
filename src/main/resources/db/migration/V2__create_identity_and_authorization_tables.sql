CREATE TABLE user_account (
    id CHAR(36) NOT NULL PRIMARY KEY,
    organization_id CHAR(36) NOT NULL,
    customer_id CHAR(36) NULL,
    driver_id CHAR(36) NULL,
    email_normalized VARCHAR(254) NULL,
    phone_normalized VARCHAR(16) NULL,
    password_hash VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    lock_level INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP(6) NULL,
    totp_secret_encrypted VARCHAR(512) NULL,
    totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_org_email UNIQUE (organization_id, email_normalized),
    CONSTRAINT uk_user_org_phone UNIQUE (organization_id, phone_normalized),
    CONSTRAINT chk_user_login_identifier CHECK (email_normalized IS NOT NULL OR phone_normalized IS NOT NULL),
    CONSTRAINT chk_user_failed_attempts CHECK (failed_login_attempts >= 0),
    INDEX ix_user_org_active (organization_id, active)
);

CREATE TABLE role (
    id CHAR(36) NOT NULL PRIMARY KEY,
    organization_id CHAR(36) NULL,
    role_key VARCHAR(80) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_role_org_key UNIQUE (organization_id, role_key),
    INDEX ix_role_active (active, role_key)
);

CREATE TABLE permission (
    id CHAR(36) NOT NULL PRIMARY KEY,
    permission_key VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    system_permission BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_permission_key UNIQUE (permission_key)
);

CREATE TABLE user_role (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    role_id CHAR(36) NOT NULL,
    organization_id CHAR(36) NOT NULL,
    warehouse_id CHAR(36) NULL,
    assignment_scope_id CHAR(36) NOT NULL,
    assigned_by CHAR(36) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id),
    CONSTRAINT uk_user_role_assignment UNIQUE (user_id, role_id, assignment_scope_id),
    INDEX ix_user_role_user_active (user_id, active),
    INDEX ix_user_role_org (organization_id, role_id)
);

CREATE TABLE role_permission (
    id CHAR(36) NOT NULL PRIMARY KEY,
    role_id CHAR(36) NOT NULL,
    permission_id CHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role(id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission(id),
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id),
    INDEX ix_role_permission_role (role_id)
);

CREATE TABLE device_session (
    id CHAR(36) NOT NULL PRIMARY KEY,
    family_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    device_name VARCHAR(160) NOT NULL,
    user_agent VARCHAR(500) NULL,
    created_ip VARCHAR(64) NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    revoked_reason VARCHAR(80) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_device_session_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT uk_device_session_family UNIQUE (family_id),
    INDEX ix_device_session_user_active (user_id, revoked_at, expires_at)
);

CREATE TABLE refresh_token (
    id CHAR(36) NOT NULL PRIMARY KEY,
    family_id CHAR(36) NOT NULL,
    device_session_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    replaced_by_token_id CHAR(36) NULL,
    created_ip VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refresh_device FOREIGN KEY (device_session_id) REFERENCES device_session(id),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_refresh_replacement FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_token(id),
    CONSTRAINT uk_refresh_hash UNIQUE (token_hash),
    INDEX ix_refresh_family_status (family_id, status),
    INDEX ix_refresh_user_expiry (user_id, expires_at)
);

CREATE TABLE password_reset_token (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    requested_ip VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT uk_password_reset_hash UNIQUE (token_hash),
    INDEX ix_password_reset_user_expiry (user_id, expires_at)
);

CREATE TABLE password_history (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_password_history_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    INDEX ix_password_history_user_time (user_id, created_at)
);

CREATE TABLE recovery_code (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_recovery_code_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT uk_recovery_user_hash UNIQUE (user_id, code_hash)
);

CREATE TABLE login_audit (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NULL,
    organization_id CHAR(36) NULL,
    identifier_fingerprint VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    failure_code VARCHAR(80) NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    correlation_id VARCHAR(64) NULL,
    INDEX ix_login_audit_user_time (user_id, occurred_at),
    INDEX ix_login_audit_fingerprint_time (identifier_fingerprint, occurred_at),
    INDEX ix_login_audit_org_time (organization_id, occurred_at)
);
