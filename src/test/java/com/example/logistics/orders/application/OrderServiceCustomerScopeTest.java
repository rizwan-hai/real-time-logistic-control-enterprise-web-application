package com.example.logistics.orders.application;

import com.example.logistics.shared.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class OrderServiceCustomerScopeTest {
    @Test
    void customerOrderListsAlwaysUseTheClaimedCustomerScope() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var service = new OrderService(db, new ObjectMapper());
        UUID organization = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("customerId", customer.toString())));

        service.listForCustomer(organization, customer);

        verify(db).queryForList(org.mockito.ArgumentMatchers.contains("organization_id=? AND customer_id=?"),
                eq(organization.toString()), eq(customer.toString()));
    }

    @Test
    void customerCannotCreateAnOrderForAnotherCustomer() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var service = new OrderService(db, new ObjectMapper());
        UUID authenticatedCustomer = UUID.randomUUID();
        var request = new OrderService.CreateOrder(UUID.randomUUID(), "WAREHOUSE", Map.of(),
                "USD", null, List.of());

        assertThatThrownBy(() -> service.createForCustomer(UUID.randomUUID(), authenticatedCustomer,
                UUID.randomUUID(), request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("authenticated customer");
        verifyNoInteractions(db);
    }
}
