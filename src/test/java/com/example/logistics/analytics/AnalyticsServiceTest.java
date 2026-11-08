package com.example.logistics.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsServiceTest {
    private JdbcTemplate jdbc;
    private AnalyticsService analytics;
    private final UUID organization = UUID.randomUUID();
    private final UUID customer = UUID.randomUUID();
    private final UUID otherCustomer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:analytics;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        source.setDriverClassName("org.h2.Driver");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE shipment(id CHAR(36),organization_id CHAR(36),shipment_number VARCHAR(40),status VARCHAR(30),created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE shipment_order(shipment_id CHAR(36),order_id CHAR(36))");
        jdbc.execute("CREATE TABLE sales_order(id CHAR(36),organization_id CHAR(36),order_number VARCHAR(40),customer_id CHAR(36))");
        jdbc.execute("CREATE TABLE shipment_stop(id CHAR(36),shipment_id CHAR(36),sequence_no INT,stop_type VARCHAR(20),status VARCHAR(20),scheduled_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE shipment_stop_history(stop_id CHAR(36),to_status VARCHAR(20),occurred_at TIMESTAMP)");
        analytics = new AnalyticsService(jdbc);
    }

    @Test
    void deliveryKpisRespectCustomerAndDateScopes() {
        addShipment(customer, LocalDate.of(2026, 11, 5), "COMPLETED", "COMPLETED",
                "2026-11-05 11:00:00", "2026-11-05 10:00:00", false);
        addShipment(customer, LocalDate.of(2026, 11, 5), "COMPLETED", "COMPLETED",
                "2026-11-05 13:00:00", "2026-11-05 14:00:00", true);
        addShipment(customer, LocalDate.of(2026, 11, 5), "FAILED", "FAILED",
                "2026-11-05 10:00:00", null, true);
        addShipment(otherCustomer, LocalDate.of(2026, 11, 5), "COMPLETED", "COMPLETED",
                "2026-11-05 10:00:00", "2026-11-05 10:30:00", false);
        addShipment(customer, LocalDate.of(2026, 10, 5), "COMPLETED", "COMPLETED",
                "2026-10-05 10:00:00", "2026-10-05 11:00:00", false);

        var filters = new AnalyticsService.Filters(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                null, null, null, null);
        Map<?, ?> report = (Map<?, ?>) analytics.dashboard(organization, customer, filters, List.of("delivery")).get("delivery");

        assertThat(report.get("deliveryStops")).isEqualTo(3L);
        assertThat(report.get("completedDeliveries")).isEqualTo(2L);
        assertThat(report.get("onTimeDeliveryPercent")).isEqualTo(new java.math.BigDecimal("50.00"));
        assertThat(report.get("delayRatePercent")).isEqualTo(new java.math.BigDecimal("50.00"));
        assertThat(report.get("firstAttemptSuccessRatePercent")).isEqualTo(new java.math.BigDecimal("50.00"));
        assertThat(report.get("failedDeliveryRatePercent")).isEqualTo(new java.math.BigDecimal("33.33"));
        assertThat(report.get("averageLeadTimeHours")).isEqualTo(new java.math.BigDecimal("4.00"));
    }

    @Test
    void deliveryRowsArePagedAndCustomerScoped() {
        addShipment(customer, LocalDate.of(2026, 11, 5), "COMPLETED", "COMPLETED",
                "2026-11-05 10:00:00", "2026-11-05 11:00:00", false);
        addShipment(otherCustomer, LocalDate.of(2026, 11, 5), "COMPLETED", "COMPLETED",
                "2026-11-05 10:00:00", "2026-11-05 11:00:00", false);
        var filters = new AnalyticsService.Filters(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                null, null, null, null);

        var rows = analytics.shipmentRows(organization, customer, filters, 0, 1);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("customerId")).isEqualTo(customer.toString());
    }

    private void addShipment(UUID customerId, LocalDate day, String shipmentStatus, String stopStatus,
                             String scheduled, String completed, boolean priorFailure) {
        String shipmentId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String stopId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO shipment(id,organization_id,shipment_number,status,created_at) VALUES(?,?,?,?,?)",
                shipmentId, organization.toString(), "SHP-" + shipmentId, shipmentStatus,
                java.sql.Timestamp.valueOf(day.atTime(8, 0)));
        jdbc.update("INSERT INTO sales_order(id,organization_id,order_number,customer_id) VALUES(?,?,?,?)",
                orderId, organization.toString(), "ORD-" + orderId, customerId.toString());
        jdbc.update("INSERT INTO shipment_order(shipment_id,order_id) VALUES(?,?)", shipmentId, orderId);
        jdbc.update("INSERT INTO shipment_stop(id,shipment_id,sequence_no,stop_type,status,scheduled_at) VALUES(?,?,1,'DELIVERY',?,?)",
                stopId, shipmentId, stopStatus, java.sql.Timestamp.valueOf(scheduled));
        if (priorFailure) jdbc.update("INSERT INTO shipment_stop_history(stop_id,to_status,occurred_at) VALUES(?,'FAILED',?)",
                stopId, java.sql.Timestamp.valueOf(day.atTime(10, 30)));
        if (completed != null) jdbc.update("INSERT INTO shipment_stop_history(stop_id,to_status,occurred_at) VALUES(?,'COMPLETED',?)",
                stopId, java.sql.Timestamp.valueOf(completed));
    }
}
