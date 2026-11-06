package com.example.logistics.tracking;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.Statement;

/** Creates a MySQL partitioned event log while keeping H2 migration tests usable. */
public class V7__TrackingSchema extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        boolean mysql = context.getConnection().getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        try (Statement s = context.getConnection().createStatement()) {
            s.execute("CREATE TABLE tracking_device (id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, vehicle_id CHAR(36) NOT NULL, driver_id CHAR(36) NULL, credential_hash CHAR(64) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE, last_seen_at TIMESTAMP(6) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE KEY uk_tracking_device_hash(credential_hash), INDEX ix_tracking_device_vehicle(organization_id,vehicle_id))");
            String table = "CREATE TABLE gps_position (id CHAR(36) NOT NULL, device_id CHAR(36) NOT NULL, organization_id CHAR(36) NOT NULL, vehicle_id CHAR(36) NOT NULL, trip_id CHAR(36) NULL, driver_id CHAR(36) NULL, latitude DECIMAL(10,7) NOT NULL, longitude DECIMAL(10,7) NOT NULL, speed_kph DECIMAL(7,2) NULL, heading DECIMAL(6,2) NULL, accuracy_m DECIMAL(7,2) NULL, battery_percent TINYINT NULL, source VARCHAR(24) NOT NULL, recorded_at TIMESTAMP(6) NOT NULL, received_at TIMESTAMP(6) NOT NULL, " + (mysql ? "location POINT SRID 4326 NULL, " : "") + "PRIMARY KEY(id,recorded_at), INDEX ix_gps_vehicle_time(vehicle_id,recorded_at), INDEX ix_gps_trip_time(trip_id,recorded_at), INDEX ix_gps_recorded(recorded_at)";
            s.execute(table + (mysql ? ") ENGINE=InnoDB PARTITION BY RANGE (TO_DAYS(recorded_at)) (PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')), PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')), PARTITION pmax VALUES LESS THAN MAXVALUE)" : ")"));
            s.execute("CREATE TABLE tracking_dedupe (device_id CHAR(36) NOT NULL, event_id VARCHAR(100) NOT NULL, request_digest CHAR(64) NOT NULL, received_at TIMESTAMP(6) NOT NULL, PRIMARY KEY(device_id,event_id), INDEX ix_tracking_dedupe_expiry(received_at))");
            s.execute("CREATE TABLE gps_position_hourly (vehicle_id CHAR(36) NOT NULL, trip_id CHAR(36) NOT NULL DEFAULT '', bucket_start TIMESTAMP(6) NOT NULL, latitude DECIMAL(10,7) NOT NULL, longitude DECIMAL(10,7) NOT NULL, avg_speed_kph DECIMAL(7,2) NULL, sample_count INT NOT NULL, PRIMARY KEY(vehicle_id,trip_id,bucket_start), INDEX ix_gps_hourly_trip(trip_id,bucket_start))");
            s.execute("CREATE TABLE geofence (id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, name VARCHAR(160) NOT NULL, kind VARCHAR(20) NOT NULL, shape_json TEXT NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE, debounce_seconds INT NOT NULL DEFAULT 60, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), INDEX ix_geofence_org(organization_id,enabled))");
            s.execute("CREATE TABLE geofence_state (vehicle_id CHAR(36) NOT NULL, geofence_id CHAR(36) NOT NULL, inside BOOLEAN NOT NULL, changed_at TIMESTAMP(6) NOT NULL, candidate_inside BOOLEAN NULL, candidate_since TIMESTAMP(6) NULL, PRIMARY KEY(vehicle_id,geofence_id))");
            s.execute("CREATE TABLE tracking_alert (id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, vehicle_id CHAR(36) NULL, trip_id CHAR(36) NULL, rule VARCHAR(40) NOT NULL, severity VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'OPEN', message VARCHAR(500) NOT NULL, assigned_to CHAR(36) NULL, acknowledged_at TIMESTAMP(6) NULL, resolved_at TIMESTAMP(6) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), INDEX ix_alert_org_status(organization_id,status,created_at))");
            s.execute("CREATE TABLE tracking_alert_history (id CHAR(36) NOT NULL PRIMARY KEY, alert_id CHAR(36) NOT NULL, actor_id CHAR(36) NULL, action VARCHAR(20) NOT NULL, note VARCHAR(500) NULL, occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), INDEX ix_alert_history(alert_id,occurred_at))");
            s.execute("CREATE TABLE public_tracking_token (id CHAR(36) NOT NULL PRIMARY KEY, shipment_id CHAR(36) NOT NULL, token_hash CHAR(64) NOT NULL, expires_at TIMESTAMP(6) NOT NULL, revoked BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE KEY uk_public_tracking_hash(token_hash), INDEX ix_public_tracking_expiry(expires_at))");
        }
    }
}
