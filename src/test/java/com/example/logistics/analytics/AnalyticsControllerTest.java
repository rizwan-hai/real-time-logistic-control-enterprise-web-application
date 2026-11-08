package com.example.logistics.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.logistics.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {
    @Mock AnalyticsService analytics;
    @Mock ReportExportService exports;
    @Mock ReportExportRunner runner;
    @Mock JdbcTemplate jdbc;

    @Test
    void customerDashboardCannotWidenItsCustomerScope() {
        UUID organization = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        var controller = new AnalyticsController(analytics, exports, runner, jdbc, new ObjectMapper().findAndRegisterModules());
        Jwt token = Jwt.withTokenValue("token").header("alg", "none").subject(actor.toString())
                .claim("organizationId", organization.toString()).claim("customerId", customer.toString())
                .claim("authorities", List.of("ROLE_CUSTOMER_USER")).build();
        when(analytics.dashboard(eq(organization), eq(customer), any(), any(), any())).thenReturn(Map.of());

        controller.dashboard(token, new AnalyticsController.ReportFilter(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), UUID.randomUUID(), null, null, null));

        ArgumentCaptor<AnalyticsService.Filters> filters = ArgumentCaptor.forClass(AnalyticsService.Filters.class);
        verify(analytics).dashboard(eq(organization), eq(customer), filters.capture(),
                eq(List.of("delivery", "financial")), eq(List.of()));
        assertThat(filters.getValue().customerId()).isEqualTo(customer);
    }

    @Test
    void warehouseDashboardRejectsUnassignedWarehouseFilter() {
        UUID organization = UUID.randomUUID();
        UUID assigned = UUID.randomUUID();
        UUID otherWarehouse = UUID.randomUUID();
        Jwt token = Jwt.withTokenValue("token").header("alg", "none").subject(UUID.randomUUID().toString())
                .claim("organizationId", organization.toString()).claim("warehouseIds", List.of(assigned.toString()))
                .claim("authorities", List.of("ROLE_WAREHOUSE_OPERATOR")).build();
        var controller = new AnalyticsController(analytics, exports, runner, jdbc, new ObjectMapper().findAndRegisterModules());

        assertThatThrownBy(() -> controller.dashboard(token, new AnalyticsController.ReportFilter(
                null, null, null, otherWarehouse, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No access to the requested warehouse");
    }
}
