package com.example.logistics.identity.application;

import com.example.logistics.identity.api.IdentityDtos;
import com.example.logistics.identity.config.IdentityProperties;
import com.example.logistics.identity.domain.DeviceSessionEntity;
import com.example.logistics.identity.domain.LoginAuditEntity;
import com.example.logistics.identity.domain.PasswordHistoryEntity;
import com.example.logistics.identity.domain.PasswordResetTokenEntity;
import com.example.logistics.identity.domain.RecoveryCodeEntity;
import com.example.logistics.identity.domain.RefreshTokenEntity;
import com.example.logistics.identity.domain.UserAccountEntity;
import com.example.logistics.identity.infrastructure.DeviceSessionRepository;
import com.example.logistics.identity.infrastructure.LoginAuditRepository;
import com.example.logistics.identity.infrastructure.PasswordHistoryRepository;
import com.example.logistics.identity.infrastructure.PasswordResetTokenRepository;
import com.example.logistics.identity.infrastructure.RecoveryCodeRepository;
import com.example.logistics.identity.infrastructure.RefreshTokenRepository;
import com.example.logistics.identity.infrastructure.UserAccountRepository;
import com.example.logistics.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AuthenticationService {
    private final UserAccountRepository users;
    private final DeviceSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resets;
    private final PasswordHistoryRepository passwordHistory;
    private final RecoveryCodeRepository recoveryCodes;
    private final LoginAuditRepository loginAudits;
    private final PasswordEncoder passwords;
    private final AuthTokenService accessTokens;
    private final PermissionResolutionService permissionResolution;
    private final TotpService totp;
    private final SecretCipher cipher;
    private final JwtDecoder decoder;
    private final IdentityMailService mail;
    private final IdentityProperties properties;
    private final Clock clock;

    public AuthenticationService(UserAccountRepository users, DeviceSessionRepository sessions, RefreshTokenRepository refreshTokens,
                                 PasswordResetTokenRepository resets, PasswordHistoryRepository passwordHistory,
                                 RecoveryCodeRepository recoveryCodes, LoginAuditRepository loginAudits, PasswordEncoder passwords,
                                 AuthTokenService accessTokens, PermissionResolutionService permissionResolution, TotpService totp,
                                 SecretCipher cipher, JwtDecoder decoder, IdentityMailService mail,
                                 IdentityProperties properties, Clock clock) {
        this.users = users; this.sessions = sessions; this.refreshTokens = refreshTokens; this.resets = resets;
        this.passwordHistory = passwordHistory; this.recoveryCodes = recoveryCodes; this.loginAudits = loginAudits;
        this.passwords = passwords; this.accessTokens = accessTokens; this.permissionResolution = permissionResolution;
        this.totp = totp; this.cipher = cipher; this.decoder = decoder; this.mail = mail; this.properties = properties; this.clock = clock;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(IdentityDtos.LoginRequest request, HttpServletRequest servletRequest) {
        UserAccountEntity user;
        String identifier;
        if (request.mfaChallenge() != null && !request.mfaChallenge().isBlank()) {
            user = challengeUser(request.mfaChallenge()); identifier = user.getEmailNormalized() != null ? user.getEmailNormalized() : user.getPhoneNormalized();
            if (!user.isActive() || isLocked(user)) { audit(user, identifier, "FAILURE", "ACCOUNT_UNAVAILABLE", servletRequest); throw invalidCredentials(); }
            if (!verifySecondFactor(user, request.totpCode(), request.recoveryCode())) {
                failLogin(user); audit(user, identifier, "FAILURE", "MFA_INVALID", servletRequest); throw invalidCredentials();
            }
        } else {
            identifier = normalizeIdentifier(request.identifier());
            user = findUser(identifier);
            if (user == null || request.password() == null || !user.isActive() || isLocked(user)
                    || !passwords.matches(request.password(), user.getPasswordHash())) {
                if (user != null) failLogin(user);
                audit(user, identifier, "FAILURE", user != null && isLocked(user) ? "ACCOUNT_LOCKED" : "CREDENTIALS_INVALID", servletRequest);
                throw invalidCredentials();
            }
            if (user.isTotpEnabled() && (blank(request.totpCode()) && blank(request.recoveryCode()))) {
                audit(user, identifier, "MFA_REQUIRED", null, servletRequest);
                return new LoginResult(IdentityDtos.LoginResponse.challenge(accessTokens.mfaChallenge(user)), null);
            }
            if (user.isTotpEnabled() && !verifySecondFactor(user, request.totpCode(), request.recoveryCode())) {
                failLogin(user); audit(user, identifier, "FAILURE", "MFA_INVALID", servletRequest); throw invalidCredentials();
            }
        }
        user.setFailedLoginAttempts(0); user.setLockLevel(0); user.setLockedUntil(null); user.setLastLoginAt(clock.instant());
        audit(user, identifier, "SUCCESS", null, servletRequest);
        return issueSession(user, request.deviceName(), servletRequest);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult refresh(String rawRefreshToken, HttpServletRequest request) {
        if (blank(rawRefreshToken)) throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Refresh token is invalid");
        var token = refreshTokens.findByTokenHashForUpdate(TokenDigests.sha256(rawRefreshToken)).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Refresh token is invalid"));
        Instant now = clock.instant();
        if (token.getStatus() == RefreshTokenEntity.Status.ROTATED) {
            revokeFamily(token.getFamilyId(), "REUSE_DETECTED", now);
            audit(users.findById(token.getUserId()).orElse(null), "refresh", "FAILURE", "REFRESH_TOKEN_REUSE_DETECTED", request);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSE_DETECTED", "Refresh token reuse detected; sign in again");
        }
        var session = sessions.findByFamilyId(token.getFamilyId()).orElse(null);
        var user = users.findById(token.getUserId()).orElse(null);
        if (token.getStatus() != RefreshTokenEntity.Status.ACTIVE || token.getExpiresAt().isBefore(now)
                || session == null || session.getRevokedAt() != null || session.getExpiresAt().isBefore(now)
                || user == null || !user.isActive()) {
            revokeFamily(token.getFamilyId(), "INVALID_REFRESH", now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Refresh token is invalid");
        }
        String nextRaw = TokenDigests.randomToken();
        var next = new RefreshTokenEntity(); next.setFamilyId(token.getFamilyId()); next.setDeviceSessionId(session.getId());
        next.setUserId(user.getId()); next.setTokenHash(TokenDigests.sha256(nextRaw)); next.setStatus(RefreshTokenEntity.Status.ACTIVE);
        next.setExpiresAt(session.getExpiresAt()); next.setCreatedIp(clientIp(request));
        refreshTokens.save(next);
        token.setStatus(RefreshTokenEntity.Status.ROTATED); token.setUsedAt(now); token.setReplacedByTokenId(next.getId());
        session.setLastSeenAt(now); sessions.save(session);
        audit(user, "refresh", "SUCCESS", null, request);
        var response = IdentityDtos.LoginResponse.authenticated(accessTokens.accessToken(user, session.getFamilyId()),
                properties.accessTokenMinutes() * 60L, view(user));
        return new LoginResult(response, nextRaw);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void logout(Jwt jwt) {
        UUID family = uuid(jwt.getClaimAsString("familyId"));
        if (family != null) revokeFamily(family, "LOGOUT", clock.instant());
    }

    @Transactional(readOnly = true)
    public List<IdentityDtos.SessionView> sessions(UUID userId, UUID currentFamily) {
        Instant now = clock.instant();
        return sessions.findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(userId, now).stream()
                .map(session -> new IdentityDtos.SessionView(session.getId(), session.getDeviceName(), session.getUserAgent(), session.getCreatedIp(),
                        session.getCreatedAt(), session.getLastSeenAt(), session.getExpiresAt(), session.getFamilyId().equals(currentFamily))).toList();
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void revokeSession(UUID actorId, UUID sessionId) {
        var session = sessions.findByIdAndUserId(sessionId, actorId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Session was not found"));
        revokeFamily(session.getFamilyId(), "REMOTE_LOGOUT", clock.instant());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void revokeSessionForAdmin(UUID organizationId, UUID targetUserId, UUID sessionId) {
        var target = users.findById(targetUserId).filter(user -> user.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "User was not found"));
        var session = sessions.findByIdAndUserId(sessionId, target.getId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Session was not found"));
        revokeFamily(session.getFamilyId(), "ADMIN_REMOTE_LOGOUT", clock.instant());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void revokeAllUserSessions(UUID userId, String reason) {
        if (!users.existsById(userId)) throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "User was not found");
        revokeAllSessions(userId, reason, clock.instant());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void requestPasswordReset(String rawIdentifier, HttpServletRequest request) {
        String identifier = normalizeIdentifier(rawIdentifier);
        UserAccountEntity user = findUser(identifier);
        if (user != null && user.isActive() && user.getEmailNormalized() != null) {
            String raw = TokenDigests.randomToken();
            var token = new PasswordResetTokenEntity(); token.setUserId(user.getId()); token.setTokenHash(TokenDigests.sha256(raw));
            token.setExpiresAt(clock.instant().plusSeconds(properties.passwordResetMinutes() * 60L)); token.setRequestedIp(clientIp(request));
            resets.save(token);
            try { mail.sendPasswordReset(user.getEmailNormalized(), raw); }
            catch (RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Password reset delivery failed"); }
        }
        audit(user, identifier, "PASSWORD_RESET_REQUESTED", null, request);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(String rawToken, String newPassword) {
        var reset = resets.findByTokenHashForUpdate(TokenDigests.sha256(rawToken)).orElseThrow(() -> invalidReset());
        Instant now = clock.instant();
        if (reset.getUsedAt() != null || reset.getExpiresAt().isBefore(now)) throw invalidReset();
        var user = users.findById(reset.getUserId()).orElseThrow(AuthenticationService::invalidReset);
        changePassword(user, newPassword);
        reset.setUsedAt(now);
        revokeAllSessions(user.getId(), "PASSWORD_RESET", now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        var user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "User was not found"));
        if (!passwords.matches(oldPassword, user.getPasswordHash())) throw new ApiException(HttpStatus.UNAUTHORIZED, "CREDENTIALS_INVALID", "Current password is incorrect");
        ensurePasswordNotReused(user, newPassword);
        changePassword(user, newPassword);
        revokeAllSessions(userId, "PASSWORD_CHANGED", clock.instant());
    }

    @Transactional(readOnly = true)
    public UserAccountEntity currentUser(UUID userId) {
        return users.findById(userId).filter(UserAccountEntity::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "User account is unavailable"));
    }

    public IdentityDtos.UserView view(UserAccountEntity user) {
        var authorities = permissionResolution.resolve(user);
        List<String> roles = authorities.stream().filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).toList();
        List<String> permissions = authorities.stream().filter(a -> !a.startsWith("ROLE_")).toList();
        return new IdentityDtos.UserView(user.getId(), user.getOrganizationId(), user.getCustomerId(), user.getDriverId(),
                user.getEmailNormalized(), user.getPhoneNormalized(), user.isActive(), user.isTotpEnabled(), roles, permissions);
    }

    @Transactional
    public IdentityDtos.TotpSetup startTotpSetup(UUID userId) {
        var user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "User was not found"));
        String secret = totp.newSecret(); user.setTotpSecretEncrypted(cipher.encrypt(secret));
        return new IdentityDtos.TotpSetup(secret, totp.provisioningUri(properties.issuer(), user.getEmailNormalized() != null ? user.getEmailNormalized() : user.getPhoneNormalized(), secret));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public IdentityDtos.RecoveryCodes enableTotp(UUID userId, String code) {
        var user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "User was not found"));
        if (user.getTotpSecretEncrypted() == null || !totp.verify(cipher.decrypt(user.getTotpSecretEncrypted()), code))
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOTP_INVALID", "Verification code is invalid");
        user.setTotpEnabled(true);
        recoveryCodes.deleteAllByUserId(userId);
        List<String> rawCodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) { String raw = randomRecoveryCode(); rawCodes.add(raw); var entity = new RecoveryCodeEntity(userId, TokenDigests.sha256(raw)); recoveryCodes.save(entity); }
        permissionResolution.evict(userId);
        return new IdentityDtos.RecoveryCodes(rawCodes);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void disableTotp(UUID userId, String password, String code) {
        var user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "User was not found"));
        boolean valid = passwords.matches(password, user.getPasswordHash()) && (user.getTotpSecretEncrypted() != null
                && totp.verify(cipher.decrypt(user.getTotpSecretEncrypted()), code) || consumeRecoveryCode(user, code));
        if (!valid) throw new ApiException(HttpStatus.UNAUTHORIZED, "CREDENTIALS_INVALID", "Verification failed");
        user.setTotpEnabled(false); user.setTotpSecretEncrypted(null); recoveryCodes.deleteAllByUserId(userId);
    }

    private LoginResult issueSession(UserAccountEntity user, String deviceName, HttpServletRequest request) {
        Instant now = clock.instant(); UUID familyId = UUID.randomUUID();
        var session = new DeviceSessionEntity(); session.setFamilyId(familyId); session.setUserId(user.getId());
        session.setDeviceName(deviceName == null || deviceName.isBlank() ? "Unknown device" : deviceName.substring(0, Math.min(deviceName.length(), 160)));
        session.setUserAgent(safe(request.getHeader("User-Agent"), 500)); session.setCreatedIp(clientIp(request));
        session.setLastSeenAt(now); session.setExpiresAt(now.plusSeconds(properties.refreshTokenDays() * 86400L)); sessions.save(session);
        String raw = TokenDigests.randomToken();
        var refresh = new RefreshTokenEntity(); refresh.setFamilyId(familyId); refresh.setDeviceSessionId(session.getId());
        refresh.setUserId(user.getId()); refresh.setTokenHash(TokenDigests.sha256(raw)); refresh.setExpiresAt(session.getExpiresAt());
        refresh.setStatus(RefreshTokenEntity.Status.ACTIVE); refresh.setCreatedIp(clientIp(request)); refreshTokens.save(refresh);
        String jwt = accessTokens.accessToken(user, familyId);
        return new LoginResult(IdentityDtos.LoginResponse.authenticated(jwt, properties.accessTokenMinutes() * 60L, view(user)), raw);
    }

    private UserAccountEntity challengeUser(String challenge) {
        try {
            Jwt jwt = decoder.decode(challenge);
            if (!"mfa_challenge".equals(jwt.getClaimAsString("purpose"))) throw invalidCredentials();
            return users.findById(UUID.fromString(jwt.getSubject())).orElseThrow(AuthenticationService::invalidCredentials);
        } catch (RuntimeException ex) { throw invalidCredentials(); }
    }

    private boolean verifySecondFactor(UserAccountEntity user, String code, String recoveryCode) {
        if (!blank(code) && user.getTotpSecretEncrypted() != null && totp.verify(cipher.decrypt(user.getTotpSecretEncrypted()), code)) return true;
        return !blank(recoveryCode) && consumeRecoveryCode(user, recoveryCode);
    }

    private boolean consumeRecoveryCode(UserAccountEntity user, String raw) {
        var code = recoveryCodes.findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), TokenDigests.sha256(raw)).orElse(null);
        if (code == null) return false; code.setUsedAt(clock.instant()); recoveryCodes.save(code); return true;
    }

    private UserAccountEntity findUser(String identifier) {
        List<UserAccountEntity> found = identifier.startsWith("+") || identifier.matches("\\d+")
                ? users.findAllByPhoneNormalized(normalizePhone(identifier)) : users.findAllByEmailNormalized(identifier);
        return found.size() == 1 ? found.getFirst() : null;
    }

    private void failLogin(UserAccountEntity user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= 5) {
            user.setLockLevel(Math.min(user.getLockLevel() + 1, 9));
            long seconds = Math.min(300L * (1L << (user.getLockLevel() - 1)), 86400L);
            user.setLockedUntil(clock.instant().plusSeconds(seconds)); user.setFailedLoginAttempts(0);
        }
    }
    private boolean isLocked(UserAccountEntity user) { return user.getLockedUntil() != null && user.getLockedUntil().isAfter(clock.instant()); }

    private void audit(UserAccountEntity user, String identifier, String outcome, String failureCode, HttpServletRequest request) {
        var audit = new LoginAuditEntity(); audit.setUserId(user == null ? null : user.getId());
        audit.setOrganizationId(user == null ? null : user.getOrganizationId());
        audit.setIdentifierFingerprint(TokenDigests.sha256(identifier == null ? "unknown" : identifier));
        audit.setOutcome(outcome); audit.setFailureCode(failureCode); audit.setIpAddress(clientIp(request));
        audit.setUserAgent(safe(request.getHeader("User-Agent"), 500)); audit.setOccurredAt(clock.instant()); audit.setCorrelationId(MDC.get("correlationId"));
        loginAudits.save(audit);
    }

    private void changePassword(UserAccountEntity user, String newPassword) {
        validatePassword(newPassword); ensurePasswordNotReused(user, newPassword);
        passwordHistory.save(new PasswordHistoryEntity(user.getId(), user.getPasswordHash()));
        user.setPasswordHash(passwords.encode(newPassword));
        var keep = passwordHistory.findTop5ByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(PasswordHistoryEntity::getId).toList();
        if (!keep.isEmpty()) passwordHistory.deleteByUserIdAndIdNotIn(user.getId(), keep);
    }

    private void ensurePasswordNotReused(UserAccountEntity user, String password) {
        if (passwords.matches(password, user.getPasswordHash()) || passwordHistory.findTop5ByUserIdOrderByCreatedAtDesc(user.getId()).stream().anyMatch(h -> passwords.matches(password, h.getPasswordHash())))
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_REUSED", "Choose a password not used recently");
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 72)
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_FAILED", "Password must be between 12 and 72 characters");
    }

    private void revokeFamily(UUID family, String reason, Instant now) {
        refreshTokens.findAllByFamilyId(family).forEach(token -> { if (token.getStatus() == RefreshTokenEntity.Status.ACTIVE) token.setStatus(RefreshTokenEntity.Status.REVOKED); });
        sessions.findByFamilyId(family).ifPresent(session -> { session.setRevokedAt(now); session.setRevokedReason(reason); sessions.save(session); });
    }
    private void revokeAllSessions(UUID userId, String reason, Instant now) {
        sessions.findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(userId, now)
                .forEach(session -> revokeFamily(session.getFamilyId(), reason, now));
    }

    private String normalizeIdentifier(String value) {
        if (blank(value)) throw invalidCredentials();
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("@")) return normalized;
        return normalizePhone(normalized);
    }
    private String normalizePhone(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("[\\s().-]", "");
        if (!normalized.matches("\\+?[1-9]\\d{7,14}")) return "";
        return normalized.startsWith("+") ? normalized : "+" + normalized;
    }
    private String randomRecoveryCode() { return TokenDigests.randomToken().substring(0, 12).toUpperCase(java.util.Locale.ROOT); }
    private String clientIp(HttpServletRequest request) { return safe(request.getRemoteAddr(), 64); }
    private String safe(String value, int max) { return value == null ? null : value.substring(0, Math.min(value.length(), max)); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private static ApiException invalidCredentials() { return new ApiException(HttpStatus.UNAUTHORIZED, "CREDENTIALS_INVALID", "Email/phone or password is incorrect"); }
    private static ApiException invalidReset() { return new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_TOKEN_INVALID", "Reset link is invalid or expired"); }
    private UUID uuid(String value) { try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException ex) { return null; } }

    public record LoginResult(IdentityDtos.LoginResponse response, String refreshCookie) {}
}
