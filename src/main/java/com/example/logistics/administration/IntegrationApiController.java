package com.example.logistics.administration;

import com.example.logistics.orders.application.OrderService;
import com.example.logistics.shared.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/orders")
public class IntegrationApiController {
    private final OrderService orders;

    public IntegrationApiController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('orders.read')")
    public ApiResponse<List<Map<String, Object>>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(orders.list(organization(jwt)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('orders.read')")
    public ApiResponse<Map<String, Object>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ApiResponse.of(orders.get(organization(jwt), id));
    }

    private static UUID organization(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("organizationId"));
    }
}
