package com.example.logistics.identity.api;

import com.example.logistics.identity.api.IdentityDtos;
import com.example.logistics.identity.application.AuthenticationService;
import com.example.logistics.identity.application.PermissionResolutionService;
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
import com.example.logistics.shared.api.ApiException;
import com.example.logistics.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/identity")
public class IdentityAdministrationController {
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserRoleRepository userRoles;
    private final RolePermissionRepository rolePermissions;
    private final PasswordEncoder passwords;
    private final PermissionResolutionService resolution;
    private final AuthenticationService authentication;

    public IdentityAdministrationController(UserAccountRepository users, RoleRepository roles, PermissionRepository permissions,
            UserRoleRepository userRoles, RolePermissionRepository rolePermissions, PasswordEncoder passwords, PermissionResolutionService resolution,
            AuthenticationService authentication) {
        this.users = users; this.roles = roles; this.permissions = permissions; this.userRoles = userRoles;
        this.rolePermissions = rolePermissions; this.passwords = passwords; this.resolution = resolution; this.authentication = authentication;
    }

    @GetMapping("/users") @PreAuthorize("hasAuthority('identity.users.read')")
    public ApiResponse<List<IdentityDtos.UserView>> users(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(users.findAllByOrganizationIdOrderByCreatedAtDesc(org(jwt)).stream().map(this::view).toList());
    }

    @PostMapping("/users") @PreAuthorize("hasAuthority('identity.users.manage')") @Transactional
    public ApiResponse<IdentityDtos.UserView> createUser(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody IdentityDtos.CreateUserRequest body) {
        UUID organizationId = org(jwt);
        String email = normalizeEmail(body.email()); String phone = normalizePhone(body.phone());
        if (email == null && phone == null) throw bad("At least one email or phone is required");
        if (email != null && users.existsByOrganizationIdAndEmailNormalized(organizationId, email)
                || phone != null && users.existsByOrganizationIdAndPhoneNormalized(organizationId, phone)) throw bad("Email or phone is already in use");
        var user = new UserAccountEntity(); user.setOrganizationId(organizationId); user.setEmailNormalized(email); user.setPhoneNormalized(phone);
        user.setPasswordHash(passwords.encode(body.password())); user.setCustomerId(body.customerId()); user.setDriverId(body.driverId()); user.setActive(true);
        users.save(user);
        if (body.roleIds() != null) for (UUID roleId : body.roleIds()) assign(jwt, user, roleId, null);
        return ApiResponse.of(view(user));
    }

    @PatchMapping("/users/{id}") @PreAuthorize("hasAuthority('identity.users.manage')") @Transactional
    public ApiResponse<IdentityDtos.UserView> updateUser(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody IdentityDtos.UpdateUserRequest body) {
        var user = users.findByIdAndOrganizationId(id, org(jwt)).orElseThrow(() -> missing("User"));
        if (body.email() != null) { var value = normalizeEmail(body.email()); if (users.existsByOrganizationIdAndEmailNormalized(org(jwt), value) && !value.equals(user.getEmailNormalized())) throw bad("Email is already in use"); user.setEmailNormalized(value); }
        if (body.phone() != null) { var value = normalizePhone(body.phone()); if (users.existsByOrganizationIdAndPhoneNormalized(org(jwt), value) && !value.equals(user.getPhoneNormalized())) throw bad("Phone is already in use"); user.setPhoneNormalized(value); }
        if (body.active() != null) { user.setActive(body.active()); if (!body.active()) authentication.revokeAllUserSessions(id, "USER_DEACTIVATED"); }
        resolution.evict(id); return ApiResponse.of(view(user));
    }

    @PostMapping("/users/{id}/roles") @PreAuthorize("hasAuthority('identity.users.manage')") @Transactional
    public ApiResponse<IdentityDtos.GenericMessage> assignRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody IdentityDtos.AssignRoleRequest body) {
        var user = users.findByIdAndOrganizationId(id, org(jwt)).orElseThrow(() -> missing("User")); assign(jwt, user, body.roleId(), body.warehouseId());
        resolution.evict(id); return ApiResponse.of(new IdentityDtos.GenericMessage("Role assigned"));
    }

    @DeleteMapping("/users/{id}/roles/{assignmentId}") @PreAuthorize("hasAuthority('identity.users.manage')") @Transactional
    public ApiResponse<IdentityDtos.GenericMessage> revokeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID assignmentId) {
        var user = users.findByIdAndOrganizationId(id, org(jwt)).orElseThrow(() -> missing("User"));
        var assignment = userRoles.findByIdAndOrganizationId(assignmentId, org(jwt)).filter(a -> a.getUserId().equals(user.getId())).orElseThrow(() -> missing("Role assignment"));
        assignment.setActive(false); resolution.evict(id); return ApiResponse.of(new IdentityDtos.GenericMessage("Role revoked"));
    }

    @GetMapping("/roles") @PreAuthorize("hasAuthority('identity.roles.read')")
    public ApiResponse<List<IdentityDtos.RoleView>> roles(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(roles.findAllByOrganizationIdIsNullOrOrganizationIdOrderByRoleKey(org(jwt)).stream().map(this::roleView).toList());
    }

    @PostMapping("/roles") @PreAuthorize("hasAuthority('identity.roles.manage')") @Transactional
    public ApiResponse<IdentityDtos.RoleView> createRole(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody IdentityDtos.CreateRoleRequest body) {
        String key = body.roleKey().trim().toUpperCase(java.util.Locale.ROOT);
        if (roles.findByRoleKeyAndOrganizationId(key, org(jwt)).isPresent() || roles.findByRoleKeyAndOrganizationIdIsNull(key).isPresent()) throw bad("Role key already exists");
        var role = new RoleEntity(); role.setOrganizationId(org(jwt)); role.setRoleKey(key); role.setDisplayName(body.displayName().trim());
        role.setDescription(body.description()); role.setSystemRole(false); roles.save(role); return ApiResponse.of(roleView(role));
    }

    @PutMapping("/roles/{id}/permissions") @PreAuthorize("hasAuthority('identity.roles.manage')") @Transactional
    public ApiResponse<IdentityDtos.RoleView> replaceRolePermissions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody IdentityDtos.ReplaceRolePermissionsRequest body) {
        var role = findManagedRole(id, org(jwt));
        if (role.isSystemRole()) throw new ApiException(HttpStatus.FORBIDDEN, "SYSTEM_ROLE_IMMUTABLE", "System roles cannot be modified");
        rolePermissions.deleteAllByRoleId(id);
        for (UUID permissionId : body.permissionIds()) {
            permissions.findById(permissionId).orElseThrow(() -> missing("Permission"));
            var link = new RolePermissionEntity(); link.setRoleId(id); link.setPermissionId(permissionId); rolePermissions.save(link);
        }
        userRoles.findAll().stream().filter(a -> a.getRoleId().equals(id)).forEach(a -> resolution.evict(a.getUserId()));
        return ApiResponse.of(roleView(role));
    }

    @GetMapping("/permissions") @PreAuthorize("hasAuthority('identity.permissions.read')")
    public ApiResponse<List<IdentityDtos.PermissionView>> permissions() {
        return ApiResponse.of(permissions.findAllByOrderByPermissionKey().stream().map(p -> new IdentityDtos.PermissionView(p.getId(), p.getPermissionKey(), p.getDescription(), p.isSystemPermission())).toList());
    }

    @PostMapping("/permissions") @PreAuthorize("hasAuthority('identity.permissions.manage')") @Transactional
    public ApiResponse<IdentityDtos.PermissionView> createPermission(@Valid @RequestBody IdentityDtos.CreatePermissionRequest body) {
        String key = body.permissionKey().trim().toLowerCase(java.util.Locale.ROOT);
        if (permissions.findByPermissionKey(key).isPresent()) throw bad("Permission key already exists");
        var permission = new PermissionEntity(); permission.setPermissionKey(key); permission.setDescription(body.description()); permission.setSystemPermission(false); permissions.save(permission);
        return ApiResponse.of(new IdentityDtos.PermissionView(permission.getId(), permission.getPermissionKey(), permission.getDescription(), permission.isSystemPermission()));
    }

    private void assign(Jwt jwt, UserAccountEntity user, UUID roleId, UUID warehouseId) {
        var role = roles.findById(roleId).filter(r -> r.getOrganizationId() == null || r.getOrganizationId().equals(org(jwt))).orElseThrow(() -> missing("Role"));
        UUID scopeId = warehouseId == null ? new UUID(0, 0) : warehouseId;
        if (userRoles.findByUserIdAndRoleIdAndAssignmentScopeId(user.getId(), roleId, scopeId).isPresent()) return;
        var assignment = new UserRoleEntity(); assignment.setUserId(user.getId()); assignment.setRoleId(roleId); assignment.setOrganizationId(org(jwt));
        assignment.setWarehouseId(warehouseId); assignment.setAssignmentScopeId(scopeId); assignment.setAssignedBy(UUID.fromString(jwt.getSubject())); userRoles.save(assignment);
    }
    private IdentityDtos.RoleView roleView(RoleEntity role) { return new IdentityDtos.RoleView(role.getId(), role.getRoleKey(), role.getDisplayName(), role.getDescription(), role.isSystemRole(), rolePermissions.findAllByRoleId(role.getId()).stream().map(RolePermissionEntity::getPermissionId).toList()); }
    private IdentityDtos.UserView view(UserAccountEntity user) { return new IdentityDtos.UserView(user.getId(), user.getOrganizationId(), user.getCustomerId(), user.getDriverId(), user.getEmailNormalized(), user.getPhoneNormalized(), user.isActive(), user.isTotpEnabled(), List.of(), List.of()); }
    private RoleEntity findManagedRole(UUID id, UUID org) { return roles.findById(id).filter(r -> r.getOrganizationId() == null || r.getOrganizationId().equals(org)).orElseThrow(() -> missing("Role")); }
    private UUID org(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("organizationId")); }
    private String normalizeEmail(String value) { return value == null || value.isBlank() ? null : value.trim().toLowerCase(java.util.Locale.ROOT); }
    private String normalizePhone(String value) { if (value == null || value.isBlank()) return null; String p = value.replaceAll("[\\s().-]", ""); if (!p.matches("\\+?[1-9]\\d{7,14}")) throw bad("Phone number must use international format"); return p.startsWith("+") ? p : "+" + p; }
    private ApiException missing(String resource) { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", resource + " was not found"); }
    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "IDENTITY_VALIDATION_FAILED", message); }
}
