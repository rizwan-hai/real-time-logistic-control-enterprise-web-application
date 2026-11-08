package com.example.logistics.analytics;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V9__AnalyticsSchema extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE report_export_job (
                        id CHAR(36) NOT NULL PRIMARY KEY,
                        organization_id CHAR(36) NOT NULL,
                        requested_by CHAR(36) NOT NULL,
                        report_type VARCHAR(40) NOT NULL,
                        export_format VARCHAR(10) NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        filters_json TEXT NOT NULL,
                        storage_key VARCHAR(100) NULL,
                        error_message VARCHAR(500) NULL,
                        row_count BIGINT NOT NULL DEFAULT 0,
                        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        started_at TIMESTAMP(6) NULL,
                        completed_at TIMESTAMP(6) NULL,
                        expires_at TIMESTAMP(6) NULL
                    )
                    """);
            statement.execute("CREATE INDEX ix_report_job_org_created ON report_export_job(organization_id, created_at)");
            statement.execute("""
                    CREATE TABLE report_schedule (
                        id CHAR(36) NOT NULL PRIMARY KEY,
                        organization_id CHAR(36) NOT NULL,
                        requested_by CHAR(36) NOT NULL,
                        report_type VARCHAR(40) NOT NULL,
                        export_format VARCHAR(10) NOT NULL,
                        frequency VARCHAR(10) NOT NULL,
                        filters_json TEXT NOT NULL,
                        enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        next_run_at TIMESTAMP(6) NOT NULL,
                        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                    )
                    """);
            statement.execute("CREATE INDEX ix_report_schedule_due ON report_schedule(enabled, next_run_at)");
            statement.execute("CREATE INDEX ix_shipment_report_org_date ON shipment(organization_id, created_at, status)");
            statement.execute("CREATE INDEX ix_trip_report_org_date ON trip(organization_id, created_at, status)");
            statement.execute("CREATE INDEX ix_stock_movement_report_org_date ON stock_movement(organization_id, occurred_at, movement_type)");
        }
    }
}
