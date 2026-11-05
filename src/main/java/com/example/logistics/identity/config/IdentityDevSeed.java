package com.example.logistics.identity.config;

import com.example.logistics.identity.domain.PermissionEntity;
import com.example.logistics.identity.domain.RoleEntity;
import com.example.logistics.identity.domain.RolePermissionEntity;
import com.example.logistics.identity.domain.UserAccountEntity;
import com.example.logistics.identity.domain.UserRoleEntity;
import com.example.logistics.identity.infrastructure.PermissionRepository;
import com.example.logistics.identity.infrastructure.RolePermissionRepository;
import com.example.logistics.identity.infrastructure.RoleRepository;
import com.example.logistics.identity.infrastructure.UserAccountRepository;
import com.example.logistics.identity.infrastructure.UserRoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Configuration
@Profile("dev")
public class IdentityDevSeed {
    private static final UUID DEV_ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Bean CommandLineRunner seedIdentity(RoleRepository roles, PermissionRepository permissions,
                                         RolePermissionRepository rolePermissions, UserAccountRepository users,
                                         UserRoleRepository userRoles, PasswordEncoder encoder, IdentityProperties properties) {
        return args -> {
            Map<String, String> permissionNames = Map.ofEntries(
                    Map.entry("identity.users.read", "Read users"), Map.entry("identity.users.manage", "Manage users"),
                    Map.entry("identity.roles.read", "Read roles"), Map.entry("identity.roles.manage", "Manage roles"),
                    Map.entry("identity.permissions.read", "Read permissions"), Map.entry("identity.permissions.manage", "Manage permissions"),
                    Map.entry("identity.sessions.manage", "Revoke user sessions"));
            var permissionRows = new java.util.HashMap<String, PermissionEntity>();
            permissionNames.forEach((key, description) -> permissionRows.put(key, permissions.findByPermissionKey(key).orElseGet(() -> {
                var permission = new PermissionEntity(); permission.setPermissionKey(key); permission.setDescription(description);
                permission.setSystemPermission(true); return permissions.save(permission);
            })));
            Map<String, String> names = Map.ofEntries(Map.entry("PLATFORM_ADMIN", "Platform administrator"), Map.entry("ORGANIZATION_ADMIN", "Organization administrator"),
                    Map.entry("CUSTOMER_USER", "Customer user"), Map.entry("DRIVER", "Driver"), Map.entry("WAREHOUSE_OPERATOR", "Warehouse operator"),
                    Map.entry("OPERATIONS_MANAGER", "Operations manager"), Map.entry("ORDER_CLERK", "Order clerk"), Map.entry("TRANSPORT_DISPATCHER", "Transport dispatcher"),
                    Map.entry("FINANCE_CLERK", "Finance clerk"), Map.entry("FINANCE_APPROVER", "Finance approver"), Map.entry("AUDITOR", "Auditor"));
            Set<String> admin = Set.of("identity.users.read", "identity.users.manage", "identity.roles.read", "identity.roles.manage",
                    "identity.permissions.read", "identity.permissions.manage", "identity.sessions.manage");
            names.forEach((key, name) -> {
                RoleEntity role = roles.findByRoleKeyAndOrganizationIdIsNull(key).orElseGet(() -> {
                    var created = new RoleEntity(); created.setRoleKey(key); created.setDisplayName(name); created.setSystemRole(true); created.setActive(true);
                    return roles.save(created);
                });
                Set<String> granted = key.equals("PLATFORM_ADMIN") ? admin : key.equals("ORGANIZATION_ADMIN")
                        ? Set.of("identity.users.read", "identity.users.manage", "identity.roles.read", "identity.roles.manage", "identity.sessions.manage") : Set.of();
                granted.forEach(permissionKey -> {
                    var permission = permissionRows.get(permissionKey);
                    if (rolePermissions.findByRoleIdAndPermissionId(role.getId(), permission.getId()).isEmpty()) {
                        var link = new RolePermissionEntity(); link.setRoleId(role.getId()); link.setPermissionId(permission.getId()); rolePermissions.save(link);
                    }
                });
            });
            if (properties.devAdminEmail() != null && !properties.devAdminEmail().isBlank()
                    && properties.devAdminPassword() != null && !properties.devAdminPassword().isBlank()
                    && !users.existsByOrganizationIdAndEmailNormalized(DEV_ORG, properties.devAdminEmail().trim().toLowerCase())) {
                var user = new UserAccountEntity(); user.setOrganizationId(DEV_ORG); user.setEmailNormalized(properties.devAdminEmail().trim().toLowerCase());
                user.setEmailVerified(true); user.setActive(true); user.setPasswordHash(encoder.encode(properties.devAdminPassword())); users.save(user);
                var role = roles.findByRoleKeyAndOrganizationIdIsNull("PLATFORM_ADMIN").orElseThrow();
                var assignment = new UserRoleEntity(); assignment.setUserId(user.getId()); assignment.setRoleId(role.getId());
                assignment.setOrganizationId(DEV_ORG); assignment.setAssignmentScopeId(new UUID(0, 0)); userRoles.save(assignment);
            }
        };
    }
}
