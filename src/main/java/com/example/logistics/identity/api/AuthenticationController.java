package com.example.logistics.identity.api;

import com.example.logistics.identity.application.AuthCookieService;
import com.example.logistics.identity.application.AuthenticationService;
import com.example.logistics.identity.api.IdentityDtos;
import com.example.logistics.identity.domain.UserAccountEntity;
import com.example.logistics.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final AuthenticationService authentication;
    private final AuthCookieService cookies;
    public AuthenticationController(AuthenticationService authentication, AuthCookieService cookies) { this.authentication = authentication; this.cookies = cookies; }

    @GetMapping("/csrf")
    public ApiResponse<IdentityDtos.CsrfView> csrf(CsrfToken token) {
        return ApiResponse.of(new IdentityDtos.CsrfView(token.getToken(), token.getHeaderName()));
    }

    @PostMapping("/login")
    public ApiResponse<IdentityDtos.LoginResponse> login(@RequestBody IdentityDtos.LoginRequest body,
                                                         HttpServletRequest request, HttpServletResponse response) {
        var issued = authentication.login(body, request);
        if (issued.refreshCookie() != null) cookies.setRefresh(response, issued.refreshCookie());
        return ApiResponse.of(issued.response());
    }

    @PostMapping("/refresh")
    public ApiResponse<IdentityDtos.LoginResponse> refresh(@org.springframework.web.bind.annotation.CookieValue(name = AuthCookieService.REFRESH_COOKIE, required = false) String refresh,
                                                           HttpServletRequest request, HttpServletResponse response) {
        var issued = authentication.refresh(refresh, request); cookies.setRefresh(response, issued.refreshCookie());
        return ApiResponse.of(issued.response());
    }

    @PostMapping("/logout")
    public ApiResponse<IdentityDtos.GenericMessage> logout(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        authentication.logout(jwt); cookies.clearRefresh(response);
        return ApiResponse.of(new IdentityDtos.GenericMessage("Signed out"));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<IdentityDtos.GenericMessage> forgot(@Valid @RequestBody IdentityDtos.ForgotPasswordRequest body, HttpServletRequest request) {
        authentication.requestPasswordReset(body.identifier(), request);
        return ApiResponse.of(new IdentityDtos.GenericMessage("If the account is eligible, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public ApiResponse<IdentityDtos.GenericMessage> reset(@Valid @RequestBody IdentityDtos.ResetPasswordRequest body) {
        authentication.resetPassword(body.token(), body.newPassword());
        return ApiResponse.of(new IdentityDtos.GenericMessage("Password updated. Sign in again."));
    }

    @PostMapping("/password")
    public ApiResponse<IdentityDtos.GenericMessage> changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody IdentityDtos.ChangePasswordRequest body) {
        authentication.changePassword(UUID.fromString(jwt.getSubject()), body.oldPassword(), body.newPassword());
        return ApiResponse.of(new IdentityDtos.GenericMessage("Password updated"));
    }

    @GetMapping("/profile")
    public ApiResponse<IdentityDtos.UserView> profile(@AuthenticationPrincipal Jwt jwt) {
        UserAccountEntity user = authentication.currentUser(UUID.fromString(jwt.getSubject()));
        return ApiResponse.of(authentication.view(user));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<IdentityDtos.SessionView>> sessions(@AuthenticationPrincipal Jwt jwt) {
        String family = jwt.getClaimAsString("familyId");
        return ApiResponse.of(authentication.sessions(UUID.fromString(jwt.getSubject()), family == null ? null : UUID.fromString(family)));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<IdentityDtos.GenericMessage> revokeSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        authentication.revokeSession(UUID.fromString(jwt.getSubject()), id);
        return ApiResponse.of(new IdentityDtos.GenericMessage("Session revoked"));
    }

    @PostMapping("/totp/setup")
    public ApiResponse<IdentityDtos.TotpSetup> setupTotp(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(authentication.startTotpSetup(UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/totp/enable")
    public ApiResponse<IdentityDtos.RecoveryCodes> enableTotp(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody IdentityDtos.TotpEnableRequest body) {
        return ApiResponse.of(authentication.enableTotp(UUID.fromString(jwt.getSubject()), body.code()));
    }

    @PostMapping("/totp/disable")
    public ApiResponse<IdentityDtos.GenericMessage> disableTotp(@AuthenticationPrincipal Jwt jwt, @RequestBody DisableTotpRequest body) {
        authentication.disableTotp(UUID.fromString(jwt.getSubject()), body.password(), body.code());
        return ApiResponse.of(new IdentityDtos.GenericMessage("Two-factor authentication disabled"));
    }

    @PostMapping("/admin/users/{userId}/sessions/{sessionId}/revoke")
    @PreAuthorize("hasAuthority('identity.sessions.manage')")
    public ApiResponse<IdentityDtos.GenericMessage> remoteLogout(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
                                                                  @PathVariable UUID sessionId) {
        authentication.revokeSessionForAdmin(UUID.fromString(jwt.getClaimAsString("organizationId")), userId, sessionId);
        return ApiResponse.of(new IdentityDtos.GenericMessage("Session revoked"));
    }

    public record DisableTotpRequest(@NotBlank String password, @NotBlank String code) {}
}
