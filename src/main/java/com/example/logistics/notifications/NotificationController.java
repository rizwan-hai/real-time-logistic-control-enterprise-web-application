package com.example.logistics.notifications;

import com.example.logistics.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping("/notifications")
    public ApiResponse<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.of(service.list(organization(jwt), user(jwt), unreadOnly, page, size));
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<Map<String, Integer>> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(Map.of("count", service.unreadCount(organization(jwt), user(jwt))));
    }

    @PatchMapping("/notifications/{id}/read")
    public ApiResponse<Map<String, Boolean>> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.markRead(organization(jwt), user(jwt), id);
        return ApiResponse.of(Map.of("read", true));
    }

    @PostMapping("/notifications/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(Map.of("updated", service.markAllRead(organization(jwt), user(jwt))));
    }

    @GetMapping("/notifications/{id}/deliveries")
    public ApiResponse<java.util.List<Map<String, Object>>> deliveries(@AuthenticationPrincipal Jwt jwt,
                                                                        @PathVariable UUID id) {
        return ApiResponse.of(service.deliveryHistory(organization(jwt), user(jwt), id));
    }

    @GetMapping("/notification-preferences")
    public ApiResponse<Map<String, Object>> preferences(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.preferences(organization(jwt), user(jwt)));
    }

    @PutMapping("/notification-preferences")
    public ApiResponse<Map<String, Object>> updatePreferences(@AuthenticationPrincipal Jwt jwt,
                                                               @Valid @RequestBody PreferencesBody body) {
        return ApiResponse.of(service.updatePreferences(organization(jwt), user(jwt),
                new NotificationService.PreferenceUpdate(body.inApp(), body.email(), body.sms(), body.webPush(), body.locale())));
    }

    private UUID organization(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("organizationId")); }
    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    public record PreferencesBody(boolean inApp, boolean email, boolean sms, boolean webPush,
                                  @NotBlank @Pattern(regexp = "en|fa") String locale) {}
}
