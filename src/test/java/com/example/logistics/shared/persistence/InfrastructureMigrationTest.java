package com.example.logistics.shared.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class InfrastructureMigrationTest {
    @Test void createsInfrastructureTablesAndConstraints() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:migration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .javaMigrations(new com.example.logistics.analytics.V9__AnalyticsSchema()).load().migrate();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        for (String table : new String[]{"NUMBER_SEQUENCE", "SETTING", "AUDIT_LOG", "ATTACHMENT", "OUTBOX_EVENT",
                "USER_ACCOUNT", "ROLE", "PERMISSION", "USER_ROLE", "ROLE_PERMISSION", "REFRESH_TOKEN",
                "PASSWORD_RESET_TOKEN", "PASSWORD_HISTORY", "LOGIN_AUDIT", "DEVICE_SESSION", "RECOVERY_CODE"}) {
            Integer count = jdbc.queryForObject("select count(*) from information_schema.tables where upper(table_name) = ?", Integer.class, table);
            assertThat(count).as("table %s should be migrated", table).isEqualTo(1);
        }
        for (String table : new String[]{"REPORT_EXPORT_JOB", "REPORT_SCHEDULE"}) {
            Integer count = jdbc.queryForObject("select count(*) from information_schema.tables where upper(table_name) = ?", Integer.class, table);
            assertThat(count).as("table %s should be migrated", table).isEqualTo(1);
        }
        String scope = UUID.randomUUID().toString();
        jdbc.update("insert into number_sequence(id, scope_id, sequence_key, current_value) values (?, ?, ?, ?)", UUID.randomUUID().toString(), scope, "invoice", 1);
        assertThat(jdbc.queryForObject("select current_value from number_sequence where scope_id = ? and sequence_key = ?", Long.class, scope, "invoice")).isEqualTo(1L);
    }
}
