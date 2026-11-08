package com.example.logistics.administration;

import com.example.logistics.identity.application.SecretCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class AdministrationServiceTest {
    private JdbcTemplate db;
    private SecretCipher cipher;
    private AdministrationService service;

    @BeforeEach
    void setUp() {
        db = mock(JdbcTemplate.class);
        cipher = mock(SecretCipher.class);
        service = new AdministrationService(db, new ObjectMapper(), cipher,
                Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void apiKeySecretIsReturnedOnceAndOnlyItsDigestIsPersisted() throws Exception {
        when(db.update(anyString(), any(Object[].class))).thenReturn(1);
        var key = service.createApiKey(UUID.randomUUID(), UUID.randomUUID(), "127.0.0.1",
                "ERP integration", List.of("orders.read"), null);

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(db, times(2)).update(anyString(), arguments.capture());
        Object[] insert = arguments.getAllValues().getFirst();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(key.secret().getBytes(StandardCharsets.UTF_8)));
        assertThat(insert[5]).isEqualTo(hash).isNotEqualTo(key.secret());
        assertThat(key.secret()).startsWith("lcp_");
        assertThat(insert[6]).isEqualTo("[\"orders.read\"]");
    }

    @Test
    void apiKeyRevocationIsOrganizationScopedAndAudited() {
        when(db.update(anyString(), any(Object[].class))).thenReturn(1);
        UUID organization = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        service.revokeApiKey(organization, actor, "192.0.2.10", keyId);

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(db, times(2)).update(anyString(), arguments.capture());
        Object[] revoke = arguments.getAllValues().getFirst();
        assertThat(revoke[1]).isEqualTo(keyId.toString());
        assertThat(revoke[2]).isEqualTo(organization.toString());
        Object[] audit = arguments.getAllValues().getLast();
        assertThat(audit[3]).isEqualTo("192.0.2.10");
        assertThat(audit[4]).isEqualTo("API_KEY_REVOKED");
    }

    @Test
    void webhookSignatureMatchesAndRejectsTamperedPayload() {
        String secret = "one-time-webhook-secret";
        String body = "{\"event\":\"OrderCreated\"}";
        String signature = service.webhookSignature(secret, body);

        assertThat(service.verifyWebhookSignature(secret, body, signature)).isTrue();
        assertThat(service.verifyWebhookSignature(secret, body + " ", signature)).isFalse();
        assertThat(service.verifyWebhookSignature(secret, body, "sha256=invalid")).isFalse();
        assertThat(WebhookDeliveryRunner.retryDelaySeconds(1)).isEqualTo(30);
        assertThat(WebhookDeliveryRunner.retryDelaySeconds(2)).isEqualTo(60);
        assertThat(WebhookDeliveryRunner.retryDelaySeconds(5)).isEqualTo(480);
        assertThat(WebhookDeliveryRunner.retryDelaySeconds(8)).isEqualTo(3600);
    }

    @Test
    void auditDiffDropsSensitiveValuesAndStoresActorIpAndSecurityFlag() {
        service.recordAudit(UUID.randomUUID(), UUID.randomUUID(), "192.0.2.3", "USER_UPDATED",
                "user", UUID.randomUUID(), Map.of("status", "ACTIVE", "email", "private@example.test"),
                Map.of("status", "LOCKED", "password", "do-not-store"), true);

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(db).update(anyString(), arguments.capture());
        Object[] stored = arguments.getValue();
        assertThat(stored[3]).isEqualTo("192.0.2.3");
        assertThat(stored[8]).isEqualTo("{\"status\":\"ACTIVE\"}");
        assertThat(stored[9]).isEqualTo("{\"status\":\"LOCKED\"}");
        assertThat(stored[10]).isEqualTo(true);
    }
}
