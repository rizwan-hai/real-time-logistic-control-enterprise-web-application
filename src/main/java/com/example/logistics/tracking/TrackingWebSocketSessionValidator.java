package com.example.logistics.tracking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Component
public class TrackingWebSocketSessionValidator {
    private final JdbcTemplate db;
    private final Clock clock;

    public TrackingWebSocketSessionValidator(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public boolean isActive(JwtAuthenticationToken authentication) {
        var token = authentication.getToken();
        if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(clock.instant())) return false;
        try {
            UUID familyId = UUID.fromString(token.getClaimAsString("familyId"));
            UUID userId = UUID.fromString(token.getSubject());
            Integer count = db.queryForObject(
                    "SELECT COUNT(*) FROM device_session WHERE family_id=? AND user_id=? AND revoked_at IS NULL AND expires_at>?",
                    Integer.class, familyId.toString(), userId.toString(), Timestamp.from(clock.instant()));
            return count != null && count > 0;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
