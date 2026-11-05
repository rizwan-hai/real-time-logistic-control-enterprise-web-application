package com.example.logistics.identity.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.io.Serializable;
import java.util.UUID;

@Component
public class IdentityPermissionEvaluator implements PermissionEvaluator {
    private final DataScopeService scopes;
    private final ObjectProvider<ScopedResourceResolver> resolvers;

    public IdentityPermissionEvaluator(DataScopeService scopes, ObjectProvider<ScopedResourceResolver> resolvers) {
        this.scopes = scopes; this.resolvers = resolvers;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object target, Object permission) {
        if (!hasAuthority(authentication, permission)) return false;
        if (target instanceof ScopeDescriptor descriptor) return scopes.canAccess(authentication, descriptor);
        return target == null;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (!hasAuthority(authentication, permission) || targetId == null || targetType == null) return false;
        try {
            UUID id = targetId instanceof UUID uuid ? uuid : UUID.fromString(targetId.toString());
            return resolvers.orderedStream().anyMatch(resolver -> resolver.resolve(id, targetType)
                    .map(scope -> scopes.canAccess(authentication, scope)).orElse(false));
        } catch (IllegalArgumentException ignored) { return false; }
    }

    private boolean hasAuthority(Authentication authentication, Object permission) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication instanceof JwtAuthenticationToken)) return false;
        return permission instanceof String key && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(key));
    }
}
