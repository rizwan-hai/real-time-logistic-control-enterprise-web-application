package com.example.logistics.identity;

import com.example.logistics.identity.api.IdentityDtos;
import com.example.logistics.identity.application.AuthenticationService;
import com.example.logistics.identity.application.TokenDigests;
import com.example.logistics.identity.domain.UserAccountEntity;
import com.example.logistics.identity.domain.PasswordResetTokenEntity;
import com.example.logistics.identity.domain.PermissionEntity;
import com.example.logistics.identity.domain.RoleEntity;
import com.example.logistics.identity.domain.RolePermissionEntity;
import com.example.logistics.identity.domain.UserRoleEntity;
import com.example.logistics.identity.application.PermissionResolutionService;
import com.example.logistics.identity.application.TotpService;
import com.example.logistics.identity.infrastructure.DeviceSessionRepository;
import com.example.logistics.identity.infrastructure.PermissionRepository;
import com.example.logistics.identity.infrastructure.PasswordResetTokenRepository;
import com.example.logistics.identity.infrastructure.RolePermissionRepository;
import com.example.logistics.identity.infrastructure.RoleRepository;
import com.example.logistics.identity.infrastructure.UserRoleRepository;
import com.example.logistics.identity.infrastructure.UserAccountRepository;
import com.example.logistics.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityAuthenticationFlowTest {
    @Autowired AuthenticationService authentication;
    @Autowired UserAccountRepository users;
    @Autowired DeviceSessionRepository sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder decoder;
    @Autowired PasswordResetTokenRepository resetTokens;
    @Autowired PermissionResolutionService permissionResolution;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired RolePermissionRepository rolePermissions;
    @Autowired UserRoleRepository userRoles;
    @Autowired TotpService totp;
    @Autowired MockMvc mvc;
    private UserAccountEntity user;
    private final String password = "Strong-Password-2026!";

    @BeforeEach void createUser() {
        user = new UserAccountEntity(); user.setOrganizationId(UUID.randomUUID()); user.setEmailNormalized(UUID.randomUUID() + "@example.test");
        user.setPasswordHash(passwords.encode(password)); user.setActive(true); users.saveAndFlush(user);
    }

    @Test void loginIssuesShortLivedJwtAndDeviceSession() {
        var result = authentication.login(new IdentityDtos.LoginRequest(user.getEmailNormalized(), password, null, null, null, "test device"), request());
        assertThat(result.response().accessToken()).isNotBlank();
        assertThat(decoder.decode(result.response().accessToken()).getExpiresAt()).isNotNull();
        assertThat(result.refreshCookie()).isNotBlank();
        assertThat(sessions.findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(user.getId(), java.time.Instant.now())).hasSize(1);
    }

    @Test void invalidPasswordFailsAndFiveFailuresApplyLockout() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authentication.login(new IdentityDtos.LoginRequest(user.getEmailNormalized(), "incorrect", null, null, null, null), request()))
                    .isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> authentication.login(new IdentityDtos.LoginRequest(user.getEmailNormalized(), password, null, null, null, null), request()))
                .isInstanceOf(ApiException.class);
    }

    @Test void refreshRotatesTokenAndReuseRevokesSessionFamily() {
        var login = authentication.login(new IdentityDtos.LoginRequest(user.getEmailNormalized(), password, null, null, null, null), request());
        String firstRefresh = login.refreshCookie();
        var rotated = authentication.refresh(firstRefresh, request());
        assertThat(rotated.refreshCookie()).isNotEqualTo(firstRefresh);
        assertThatThrownBy(() -> authentication.refresh(firstRefresh, request())).isInstanceOf(ApiException.class)
                .hasMessageContaining("reuse detected");
        assertThatThrownBy(() -> authentication.refresh(rotated.refreshCookie(), request())).isInstanceOf(ApiException.class);
    }

    @Test void logoutRevokesOnlyCurrentSessionFamilyAndRejectsExistingAccessToken() throws Exception {
        var login = authentication.login(new IdentityDtos.LoginRequest(user.getEmailNormalized(), password, null, null, null, null), request());
        var jwt = decoder.decode(login.response().accessToken()); authentication.logout(jwt);
        mvc.perform(get("/api/v1/auth/profile").header("Authorization", "Bearer " + login.response().accessToken()))
                .andExpect(status().isUnauthorized());
        assertThat(sessions.findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(user.getId(), java.time.Instant.now())).isEmpty();
        assertThatThrownBy(() -> authentication.refresh(login.refreshCookie(), request())).isInstanceOf(ApiException.class);
    }

    @Test void passwordResetChangesPasswordAndPreventsReusingPreviousPassword() {
        String rawToken = "one-time-reset-token";
        var reset = new PasswordResetTokenEntity(); reset.setUserId(user.getId());
        reset.setTokenHash(TokenDigests.sha256(rawToken)); reset.setExpiresAt(java.time.Instant.now().plusSeconds(600)); resetTokens.saveAndFlush(reset);
        String replacement = "Another-Strong-Password-2026!";
        authentication.resetPassword(rawToken, replacement);
        assertThat(passwords.matches(replacement, users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThatThrownBy(() -> authentication.changePassword(user.getId(), replacement, password)).isInstanceOf(ApiException.class)
                .hasMessageContaining("recently");
    }

    @Test void rbacResolvesAssignedRolePermissionsAndIgnoresInactiveRole() {
        var permission = new PermissionEntity(); permission.setPermissionKey("orders.read"); permissions.saveAndFlush(permission);
        var role = new RoleEntity(); role.setRoleKey("TEST_READER"); role.setDisplayName("Test reader"); role.setOrganizationId(user.getOrganizationId());
        roles.saveAndFlush(role);
        var rolePermission = new RolePermissionEntity(); rolePermission.setRoleId(role.getId()); rolePermission.setPermissionId(permission.getId()); rolePermissions.saveAndFlush(rolePermission);
        var assignment = new UserRoleEntity(); assignment.setUserId(user.getId()); assignment.setRoleId(role.getId());
        assignment.setOrganizationId(user.getOrganizationId()); assignment.setAssignmentScopeId(new UUID(0, 0)); userRoles.saveAndFlush(assignment);
        var resolved = permissionResolution.resolveFromDatabase(user);
        assertThat(resolved).contains("ROLE_TEST_READER", "orders.read");
        role.setActive(false); roles.saveAndFlush(role);
        assertThat(permissionResolution.resolveFromDatabase(user)).doesNotContain("ROLE_TEST_READER", "orders.read");
    }

    @Test void twoFactorLoginAcceptsTotpAndOneTimeRecoveryCode() throws Exception {
        var setup = authentication.startTotpSetup(user.getId());
        var recovery = authentication.enableTotp(user.getId(), currentCode(setup.secret()));
        var challenge = authentication.login(new IdentityDtos.LoginRequest(user.getEmailNormalized(), password, null, null, null, null), request()).response();
        assertThat(challenge.mfaRequired()).isTrue();
        mvc.perform(get("/api/v1/auth/profile").header("Authorization", "Bearer " + challenge.mfaChallenge()))
                .andExpect(status().isUnauthorized());
        var totpLogin = authentication.login(new IdentityDtos.LoginRequest(null, null, challenge.mfaChallenge(), currentCode(setup.secret()), null, null), request());
        assertThat(totpLogin.response().mfaRequired()).isFalse();
        var nextChallenge = authentication.login(new IdentityDtos.LoginRequest(user.getEmailNormalized(), password, null, null, null, null), request()).response();
        var recoveryLogin = authentication.login(new IdentityDtos.LoginRequest(null, null, nextChallenge.mfaChallenge(), null, recovery.codes().getFirst(), null), request());
        assertThat(recoveryLogin.response().mfaRequired()).isFalse();
    }

    private String currentCode(String secret) throws Exception {
        var method = TotpService.class.getDeclaredMethod("code", String.class, long.class);
        method.setAccessible(true);
        return (String) method.invoke(totp, secret, java.time.Instant.now().getEpochSecond() / 30);
    }

    private HttpServletRequest request() { var req = new MockHttpServletRequest(); req.setRemoteAddr("127.0.0.1"); req.addHeader("User-Agent", "test-suite"); return req; }
}
