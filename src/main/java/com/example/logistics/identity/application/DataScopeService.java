package com.example.logistics.identity.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.UUID;

@Service
public class DataScopeService {
    public boolean canAccess(Authentication authentication, ScopeDescriptor resource) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth) || resource == null) return false;
        var jwt = jwtAuth.getToken();
        UUID organizationId = uuidClaim(jwt.getClaimAsString("organizationId"));
        if (organizationId == null || !organizationId.equals(resource.organizationId())) return false;
        var authorities = authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        if (authorities.contains("ROLE_PLATFORM_ADMIN")) return true;
        if (authorities.contains("ROLE_CUSTOMER_USER")) {
            UUID customerId = uuidClaim(jwt.getClaimAsString("customerId"));
            return customerId != null && customerId.equals(resource.customerId());
        }
        if (authorities.contains("ROLE_DRIVER")) {
            UUID driverId = uuidClaim(jwt.getClaimAsString("driverId"));
            return driverId != null && driverId.equals(resource.driverId());
        }
        if (authorities.contains("ROLE_WAREHOUSE_OPERATOR")) {
            UUID warehouseId = resource.warehouseId();
            Object claim = jwt.getClaims().get("warehouseIds");
            if (warehouseId == null || !(claim instanceof Collection<?> ids)) return false;
            return ids.stream().anyMatch(id -> warehouseId.toString().equals(String.valueOf(id)));
        }
        return true;
    }

    private UUID uuidClaim(String value) {
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
