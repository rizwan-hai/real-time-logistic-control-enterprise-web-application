package com.example.logistics.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class IdentityDtos {
    private IdentityDtos() {}

    public record LoginRequest(String identifier, String password, String mfaChallenge, String totpCode,
                               String recoveryCode, String deviceName) {}
    public record LoginResponse(boolean mfaRequired, String mfaChallenge, String accessToken,
                                String tokenType, long expiresInSeconds, UserView user) {
        public static LoginResponse challenge(String token) { return new LoginResponse(true, token, null, null, 0, null); }
        public static LoginResponse authenticated(String token, long ttl, UserView user) { return new LoginResponse(false, null, token, "Bearer", ttl, user); }
    }
    public record UserView(UUID id, UUID organizationId, UUID customerId, UUID driverId, String email,
                           String phone, boolean active, boolean totpEnabled, List<String> roles, List<String> permissions) {}
    public record CsrfView(String token, String headerName) {}
    public record GenericMessage(String message) {}
    public record ForgotPasswordRequest(@NotBlank @Size(max = 254) String identifier) {}
    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 12, max = 72) String newPassword) {}
    public record ChangePasswordRequest(@NotBlank String oldPassword, @NotBlank @Size(min = 12, max = 72) String newPassword) {}
    public record TotpSetup(String secret, String provisioningUri) {}
    public record TotpEnableRequest(@NotBlank String code) {}
    public record RecoveryCodes(List<String> codes) {}
    public record SessionView(UUID id, String deviceName, String userAgent, String ipAddress,
                              Instant createdAt, Instant lastSeenAt, Instant expiresAt, boolean current) {}
    public record CreateUserRequest(@Email String email, String phone, @NotBlank @Size(min = 12, max = 72) String password,
                                    UUID customerId, UUID driverId, List<UUID> roleIds, List<UUID> warehouseIds) {}
    public record UpdateUserRequest(Boolean active, String email, String phone) {}
    public record AssignRoleRequest(@NotNull UUID roleId, UUID warehouseId) {}
    public record CreateRoleRequest(@NotBlank @Size(max = 80) String roleKey,
                                    @NotBlank @Size(max = 120) String displayName, @Size(max = 500) String description) {}
    public record CreatePermissionRequest(@NotBlank @Size(max = 120) String permissionKey, @Size(max = 500) String description) {}
    public record ReplaceRolePermissionsRequest(@NotNull List<UUID> permissionIds) {}
    public record RoleView(UUID id, String roleKey, String displayName, String description, boolean systemRole, List<UUID> permissionIds) {}
    public record PermissionView(UUID id, String permissionKey, String description, boolean systemPermission) {}
}
