package com.example.logistics.identity;

import com.example.logistics.identity.application.DataScopeService;
import com.example.logistics.identity.application.ScopeDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class DataScopeServiceTest {
    private final DataScopeService scopes = new DataScopeService();
    private final UUID org = UUID.randomUUID();

    @Test void customerCanOnlyAccessOwnCustomerRecords() {
        UUID customer = UUID.randomUUID(); var auth = auth("customerId", customer, "ROLE_CUSTOMER_USER");
        assertThat(scopes.canAccess(auth, new ScopeDescriptor(org, customer, null, null))).isTrue();
        assertThat(scopes.canAccess(auth, new ScopeDescriptor(org, UUID.randomUUID(), null, null))).isFalse();
        assertThat(scopes.canAccess(auth, new ScopeDescriptor(UUID.randomUUID(), customer, null, null))).isFalse();
    }

    @Test void driverCanOnlyAccessAssignedDriverRecords() {
        UUID driver = UUID.randomUUID(); var auth = auth("driverId", driver, "ROLE_DRIVER");
        assertThat(scopes.canAccess(auth, new ScopeDescriptor(org, null, driver, null))).isTrue();
        assertThat(scopes.canAccess(auth, new ScopeDescriptor(org, null, UUID.randomUUID(), null))).isFalse();
    }

    @Test void warehouseUserMustHaveWarehouseInAssignedScope() {
        UUID warehouse = UUID.randomUUID(); var jwt = base().claim("warehouseIds", List.of(warehouse.toString())).build();
        var auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_OPERATOR")));
        assertThat(scopes.canAccess(auth, new ScopeDescriptor(org, null, null, warehouse))).isTrue();
        assertThat(scopes.canAccess(auth, new ScopeDescriptor(org, null, null, UUID.randomUUID()))).isFalse();
    }

    private JwtAuthenticationToken auth(String claim, UUID id, String role) {
        var jwt = base().claim(claim, id.toString()).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role)));
    }
    private Jwt.Builder base() { return Jwt.withTokenValue("test").header("alg", "none").claim("organizationId", org.toString()); }
}
