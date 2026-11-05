package com.example.logistics.identity.application;

import com.example.logistics.identity.domain.PermissionEntity;
import com.example.logistics.identity.domain.RoleEntity;
import com.example.logistics.identity.domain.UserAccountEntity;
import com.example.logistics.identity.domain.UserRoleEntity;
import com.example.logistics.identity.infrastructure.PermissionRepository;
import com.example.logistics.identity.infrastructure.RolePermissionRepository;
import com.example.logistics.identity.infrastructure.RoleRepository;
import com.example.logistics.identity.infrastructure.UserRoleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class PermissionResolutionService {
    private static final Logger log = LoggerFactory.getLogger(PermissionResolutionService.class);
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final PermissionRepository permissions;

    public PermissionResolutionService(StringRedisTemplate redis, ObjectMapper mapper, UserRoleRepository userRoles,
                                      RoleRepository roles, RolePermissionRepository rolePermissions,
                                      PermissionRepository permissions) {
        this.redis = redis; this.mapper = mapper; this.userRoles = userRoles; this.roles = roles;
        this.rolePermissions = rolePermissions; this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<String> resolve(UserAccountEntity user) {
        String key = "identity:permissions:v1:" + user.getId();
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) return mapper.readValue(cached, STRING_LIST);
        } catch (Exception ex) { log.debug("Permission cache read failed; resolving from database", ex); }
        Set<String> authorities = resolveFromDatabase(user);
        List<String> resolved = List.copyOf(authorities);
        try { redis.opsForValue().set(key, mapper.writeValueAsString(resolved), TTL); }
        catch (Exception ex) { log.debug("Permission cache write failed", ex); }
        return resolved;
    }

    @Transactional(readOnly = true)
    public Set<String> resolveFromDatabase(UserAccountEntity user) {
        List<UserRoleEntity> assignments = userRoles.findAllByUserIdAndOrganizationIdAndActiveTrue(user.getId(), user.getOrganizationId());
        Set<UUID> roleIds = new java.util.HashSet<>();
        Set<String> authorities = new TreeSet<>();
        assignments.forEach(assignment -> roleIds.add(assignment.getRoleId()));
        if (!roleIds.isEmpty()) {
        List<RoleEntity> assignedRoles = roles.findAllById(roleIds);
        Set<UUID> activeRoleIds = new java.util.HashSet<>();
        assignedRoles.stream().filter(RoleEntity::isActive).forEach(role -> {
            authorities.add("ROLE_" + role.getRoleKey()); activeRoleIds.add(role.getId());
        });
        var rolePermissionRows = rolePermissions.findAllByRoleIdIn(List.copyOf(activeRoleIds));
            Set<UUID> permissionIds = new java.util.HashSet<>();
            rolePermissionRows.forEach(row -> permissionIds.add(row.getPermissionId()));
            permissions.findAllById(permissionIds).stream().map(PermissionEntity::getPermissionKey).forEach(authorities::add);
        }
        return authorities;
    }

    public List<String> warehouseScopes(UserAccountEntity user) {
        return userRoles.findAllByUserIdAndOrganizationIdAndActiveTrue(user.getId(), user.getOrganizationId()).stream()
                .map(UserRoleEntity::getWarehouseId).filter(java.util.Objects::nonNull).map(UUID::toString).distinct().toList();
    }

    public void evict(UUID userId) {
        try { redis.delete("identity:permissions:v1:" + userId); }
        catch (Exception ex) { log.debug("Permission cache eviction failed", ex); }
    }

    public void evictOrganization(UUID organizationId) {
        userRoles.findAll().stream().filter(row -> organizationId.equals(row.getOrganizationId()))
                .map(UserRoleEntity::getUserId).distinct().forEach(this::evict);
    }
}
