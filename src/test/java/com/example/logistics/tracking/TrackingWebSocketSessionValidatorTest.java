package com.example.logistics.tracking;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TrackingWebSocketSessionValidatorTest {
    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    @Test
    void acceptsOnlyAnUnexpiredPersistedSessionFamily() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        when(db.queryForObject(anyString(), eq(Integer.class), any(), any(), any())).thenReturn(1);
        var validator = new TrackingWebSocketSessionValidator(db, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(validator.isActive(authentication(userId, familyId, NOW.plusSeconds(60)))).isTrue();
        verify(db).queryForObject(
                eq("SELECT COUNT(*) FROM device_session WHERE family_id=? AND user_id=? AND revoked_at IS NULL AND expires_at>?"),
                eq(Integer.class), eq(familyId.toString()), eq(userId.toString()), any());
    }

    @Test
    void rejectsExpiredAccessTokensWithoutQueryingSessionStorage() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var validator = new TrackingWebSocketSessionValidator(db, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(validator.isActive(authentication(UUID.randomUUID(), UUID.randomUUID(), NOW))).isFalse();
        verifyNoInteractions(db);
    }

    @Test
    void rejectsRevokedOrMissingSessionFamilies() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject(anyString(), eq(Integer.class), any(), any(), any())).thenReturn(0);
        var validator = new TrackingWebSocketSessionValidator(db, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(validator.isActive(authentication(UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(60)))).isFalse();
    }

    private JwtAuthenticationToken authentication(UUID userId, UUID familyId, Instant expiresAt) {
        Jwt token = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("familyId", familyId.toString())
                .expiresAt(expiresAt)
                .issuedAt(NOW.minusSeconds(60))
                .build();
        return new JwtAuthenticationToken(token);
    }
}
