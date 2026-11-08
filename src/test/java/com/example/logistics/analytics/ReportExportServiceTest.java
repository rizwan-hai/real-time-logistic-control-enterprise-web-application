package com.example.logistics.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportExportServiceTest {
    private JdbcTemplate jdbc;
    private ReportExportService exports;
    private final UUID organization = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final AnalyticsService.Filters filters = new AnalyticsService.Filters(
            LocalDate.now().minusDays(1), LocalDate.now(), null, null, null, null);

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:report-export;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        source.setDriverClassName("org.h2.Driver");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE report_export_job(id CHAR(36) PRIMARY KEY,organization_id CHAR(36),requested_by CHAR(36),report_type VARCHAR(40),export_format VARCHAR(10),status VARCHAR(16),filters_json TEXT,storage_key VARCHAR(100),error_message VARCHAR(500),row_count BIGINT DEFAULT 0,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,started_at TIMESTAMP,completed_at TIMESTAMP,expires_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE shipment(id CHAR(36),organization_id CHAR(36),shipment_number VARCHAR(40),status VARCHAR(30),created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE shipment_order(shipment_id CHAR(36),order_id CHAR(36))");
        jdbc.execute("CREATE TABLE sales_order(id CHAR(36),organization_id CHAR(36),order_number VARCHAR(40),customer_id CHAR(36))");
        jdbc.execute("CREATE TABLE shipment_stop(id CHAR(36),shipment_id CHAR(36),sequence_no INT,stop_type VARCHAR(20),status VARCHAR(20),scheduled_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE shipment_stop_history(stop_id CHAR(36),to_status VARCHAR(20),occurred_at TIMESTAMP)");
        var shipment = UUID.randomUUID().toString();
        var order = UUID.randomUUID().toString();
        var stop = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO shipment(id,organization_id,shipment_number,status,created_at) VALUES(?,?,?,'DELIVERED',?)",
                shipment, organization.toString(), "SHP-0001", java.sql.Timestamp.valueOf(LocalDate.now().atTime(10, 0)));
        jdbc.update("INSERT INTO sales_order(id,organization_id,order_number,customer_id) VALUES(?,?,?,?)",
                order, organization.toString(), "ORD-0001", UUID.randomUUID().toString());
        jdbc.update("INSERT INTO shipment_order(shipment_id,order_id) VALUES(?,?)", shipment, order);
        jdbc.update("INSERT INTO shipment_stop(id,shipment_id,sequence_no,stop_type,status,scheduled_at) VALUES(?,?,1,'DELIVERY','COMPLETED',?)",
                stop, shipment, java.sql.Timestamp.valueOf(LocalDate.now().atTime(11, 0)));
        jdbc.update("INSERT INTO shipment_stop_history(stop_id,to_status,occurred_at) VALUES(?,'COMPLETED',?)",
                stop, java.sql.Timestamp.valueOf(LocalDate.now().atTime(10, 30)));
        exports = new ReportExportService(jdbc, new AnalyticsService(jdbc), new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void csvExportPersistsLifecycleAndRestrictsDownloadsToRequester() throws Exception {
        UUID jobId = exports.create(organization, actor, filters, "CSV");
        assertThat(jdbc.queryForObject("SELECT status FROM report_export_job WHERE id=?", String.class, jobId.toString()))
                .isEqualTo("QUEUED");

        exports.generate(jobId, organization, actor, filters, "CSV");

        var file = exports.downloadable(organization, actor, jobId, false);
        assertThat(Files.readString(file)).contains("SHP-0001", "ORD-0001", "COMPLETED");
        assertThat(jdbc.queryForObject("SELECT row_count FROM report_export_job WHERE id=?", Long.class, jobId.toString())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT status FROM report_export_job WHERE id=?", String.class, jobId.toString()))
                .isEqualTo("COMPLETED");
        assertThatThrownBy(() -> exports.downloadable(organization, UUID.randomUUID(), jobId, false))
                .isInstanceOf(com.example.logistics.shared.api.ApiException.class);
        Files.deleteIfExists(file);
    }

    @Test
    void xlsxAndPdfExportsAreGeneratedWithoutChangingTheJobContract() throws Exception {
        for (String format : new String[]{"XLSX", "PDF"}) {
            UUID jobId = exports.create(organization, actor, filters, format);
            exports.generate(jobId, organization, actor, filters, format);
            var file = exports.downloadable(organization, actor, jobId, false);
            byte[] content = Files.readAllBytes(file);
            assertThat(content).isNotEmpty();
            if (format.equals("PDF")) assertThat(new String(content, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
            else assertThat(content[0]).isEqualTo((byte) 'P');
            Files.deleteIfExists(file);
        }
    }
}
