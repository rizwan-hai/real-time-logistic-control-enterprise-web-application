package com.example.logistics.warehouse.application;

import com.example.logistics.shared.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WarehouseServiceOrganizationScopeTest {
    @Test
    void allocationRejectsAnOrderOutsideTheAuthenticatedOrganization() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var service = new WarehouseService(db, new ObjectMapper());
        UUID organization = UUID.randomUUID();
        UUID foreignOrder = UUID.randomUUID();
        when(db.queryForList(eq("SELECT id FROM sales_order WHERE id=? AND organization_id=? FOR UPDATE"),
                eq(String.class), eq(foreignOrder.toString()), eq(organization.toString()))).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> service.allocate(organization, UUID.randomUUID(), foreignOrder, "FEFO"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Warehouse resource not found");

        verify(db).queryForList("SELECT id FROM sales_order WHERE id=? AND organization_id=? FOR UPDATE",
                String.class, foreignOrder.toString(), organization.toString());
        verifyNoMoreInteractions(db);
    }
}
