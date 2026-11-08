package com.example.logistics.administration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V10__AdministrationSchema extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE audit_log ADD COLUMN ip_address VARCHAR(64) NULL");
            statement.execute("ALTER TABLE audit_log ADD COLUMN before_json TEXT NULL");
            statement.execute("ALTER TABLE audit_log ADD COLUMN after_json TEXT NULL");
            statement.execute("ALTER TABLE audit_log ADD COLUMN security_event BOOLEAN NOT NULL DEFAULT FALSE");
            statement.execute("""
                    CREATE TABLE api_key (
                        id CHAR(36) NOT NULL PRIMARY KEY,
                        organization_id CHAR(36) NOT NULL,
                        created_by CHAR(36) NOT NULL,
                        name VARCHAR(120) NOT NULL,
                        key_prefix VARCHAR(16) NOT NULL,
                        key_hash CHAR(64) NOT NULL,
                        scopes_json TEXT NOT NULL,
                        expires_at TIMESTAMP(6) NULL,
                        revoked_at TIMESTAMP(6) NULL,
                        last_used_at TIMESTAMP(6) NULL,
                        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        CONSTRAINT uk_api_key_hash UNIQUE (key_hash),
                        INDEX ix_api_key_org_created (organization_id, created_at)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE webhook_endpoint (
                        id CHAR(36) NOT NULL PRIMARY KEY,
                        organization_id CHAR(36) NOT NULL,
                        created_by CHAR(36) NOT NULL,
                        name VARCHAR(120) NOT NULL,
                        target_url VARCHAR(2048) NOT NULL,
                        secret_encrypted TEXT NOT NULL,
                        events_json TEXT NOT NULL,
                        active BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        INDEX ix_webhook_org_active (organization_id, active)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE webhook_delivery (
                        id CHAR(36) NOT NULL PRIMARY KEY,
                        organization_id CHAR(36) NOT NULL,
                        endpoint_id CHAR(36) NOT NULL,
                        event_id CHAR(36) NOT NULL,
                        event_type VARCHAR(200) NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        attempt_count INT NOT NULL DEFAULT 0,
                        claimed_at TIMESTAMP(6) NULL,
                        next_attempt_at TIMESTAMP(6) NULL,
                        last_status_code INT NULL,
                        last_error VARCHAR(500) NULL,
                        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        completed_at TIMESTAMP(6) NULL,
                        CONSTRAINT uk_webhook_delivery_event UNIQUE (endpoint_id, event_id),
                        INDEX ix_webhook_delivery_retry (status, next_attempt_at)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE webhook_delivery_attempt (
                        id CHAR(36) NOT NULL PRIMARY KEY,
                        delivery_id CHAR(36) NOT NULL,
                        attempt_number INT NOT NULL,
                        status_code INT NULL,
                        error_message VARCHAR(500) NULL,
                        attempted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        INDEX ix_webhook_attempt_delivery (delivery_id, attempted_at)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE webhook_event_receipt (
                        event_id CHAR(36) NOT NULL PRIMARY KEY,
                        processed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE business_calendar (
                        id CHAR(36) NOT NULL PRIMARY KEY,
                        organization_id CHAR(36) NOT NULL,
                        calendar_key VARCHAR(80) NOT NULL,
                        name VARCHAR(120) NOT NULL,
                        locale VARCHAR(16) NOT NULL DEFAULT 'en',
                        timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
                        weekend_days VARCHAR(40) NOT NULL DEFAULT '5,6',
                        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        CONSTRAINT uk_business_calendar_org_key UNIQUE (organization_id, calendar_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE business_holiday (
                        id CHAR(36) NOT NULL PRIMARY KEY,
                        organization_id CHAR(36) NOT NULL,
                        calendar_key VARCHAR(80) NOT NULL,
                        holiday_date DATE NOT NULL,
                        name VARCHAR(120) NOT NULL,
                        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        CONSTRAINT uk_business_holiday_org_calendar_date UNIQUE (organization_id, calendar_key, holiday_date),
                        INDEX ix_business_holiday_date (organization_id, holiday_date)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE customer_address (
                        id CHAR(36) NOT NULL PRIMARY KEY,
                        organization_id CHAR(36) NOT NULL,
                        customer_id CHAR(36) NOT NULL,
                        label VARCHAR(100) NOT NULL,
                        address_json TEXT NOT NULL,
                        is_default BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        INDEX ix_customer_address_owner (organization_id, customer_id, is_default)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE customer_document (
                        id CHAR(36) NOT NULL PRIMARY KEY,
                        organization_id CHAR(36) NOT NULL,
                        customer_id CHAR(36) NOT NULL,
                        attachment_id CHAR(36) NOT NULL,
                        document_type VARCHAR(16) NOT NULL,
                        document_number VARCHAR(80) NOT NULL,
                        shipment_id CHAR(36) NULL,
                        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        CONSTRAINT fk_customer_document_attachment FOREIGN KEY (attachment_id) REFERENCES attachment(id),
                        CONSTRAINT uk_customer_document_attachment UNIQUE (attachment_id),
                        CONSTRAINT chk_customer_document_type CHECK (document_type IN ('INVOICE','POD')),
                        INDEX ix_customer_document_owner (organization_id, customer_id, document_type, created_at)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE customer_portal_profile (
                        organization_id CHAR(36) NOT NULL,
                        customer_id CHAR(36) NOT NULL,
                        preferred_locale VARCHAR(5) NOT NULL DEFAULT 'en',
                        updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        PRIMARY KEY (organization_id, customer_id)
                    )
                    """);
        }
    }
}
