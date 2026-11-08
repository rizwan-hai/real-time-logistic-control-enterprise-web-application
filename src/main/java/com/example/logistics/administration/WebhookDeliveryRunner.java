package com.example.logistics.administration;

import com.example.logistics.identity.application.SecretCipher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class WebhookDeliveryRunner {
    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryRunner.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final Set<String> PRIVATE_FIELDS = Set.of(
            "password", "passwordhash", "token", "secret", "authorization", "email", "phone",
            "address", "ipaddress", "totpsecret", "recoverycode");

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final SecretCipher cipher;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final HttpClient http;

    public WebhookDeliveryRunner(JdbcTemplate db, ObjectMapper json, SecretCipher cipher, Clock clock,
                                 PlatformTransactionManager transactionManager) {
        this.db = db;
        this.json = json;
        this.cipher = cipher;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Scheduled(fixedDelayString = "${app.webhooks.outbox-poll-millis:2000}")
    public void enqueueOutboxEvents() {
        List<String> eventIds = db.queryForList("""
                SELECT e.id FROM outbox_event e
                WHERE NOT EXISTS (SELECT 1 FROM webhook_event_receipt r WHERE r.event_id=e.id)
                ORDER BY e.occurred_at LIMIT 50
                """, String.class);
        for (String eventId : eventIds) {
            try {
                transactions.executeWithoutResult(status -> enqueueEvent(eventId));
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                log.debug("Webhook outbox event was concurrently enqueued: {}", eventId);
            } catch (RuntimeException exception) {
                log.error("Unable to enqueue webhook outbox event {}: {}", eventId, exception.getClass().getSimpleName());
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.webhooks.delivery-poll-millis:1000}")
    public void deliverDueEvents() {
        db.update("""
                UPDATE webhook_delivery SET status='FAILED',completed_at=?,claimed_at=NULL,
                    last_error='Delivery worker did not recover within two minutes'
                WHERE status='RUNNING' AND attempt_count>=?
                    AND claimed_at<?
                """, java.sql.Timestamp.from(clock.instant()), MAX_ATTEMPTS,
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofMinutes(2))));
        db.update("""
                UPDATE webhook_delivery SET status='RETRY',claimed_at=NULL,next_attempt_at=?
                WHERE status='RUNNING' AND attempt_count<?
                    AND claimed_at<?
                """, java.sql.Timestamp.from(clock.instant()),
                MAX_ATTEMPTS,
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofMinutes(2))));
        List<String> ids = db.queryForList("""
                SELECT id FROM webhook_delivery
                WHERE status IN ('PENDING','RETRY') AND (next_attempt_at IS NULL OR next_attempt_at<=?)
                ORDER BY created_at LIMIT 25
                """, String.class, java.sql.Timestamp.from(clock.instant()));
        for (String id : ids) {
            try {
                deliver(id);
            } catch (RuntimeException exception) {
                log.error("Unexpected failure processing webhook delivery {}: {}", id, exception.getClass().getSimpleName());
                recordFailure(id, null, "DELIVERY_WORKER_ERROR", exception.getClass().getSimpleName());
            }
        }
    }

    private void enqueueEvent(String eventId) {
        List<OutboxEvent> events = db.query("""
                SELECT id,organization_id,event_type,schema_version,aggregate_type,aggregate_id,
                       occurred_at,payload
                FROM outbox_event WHERE id=? FOR UPDATE
                """, (rs, row) -> new OutboxEvent(rs.getString("id"), rs.getString("organization_id"),
                rs.getString("event_type"), rs.getInt("schema_version"), rs.getString("aggregate_type"),
                rs.getString("aggregate_id"), rs.getTimestamp("occurred_at").toInstant(),
                parse(rs.getString("payload"))), eventId);
        if (events.isEmpty()) return;
        OutboxEvent event = events.getFirst();
        if (event.organizationId() != null) {
            List<Endpoint> endpoints = db.query("""
                    SELECT id,secret_encrypted,events_json FROM webhook_endpoint
                    WHERE organization_id=? AND active=TRUE
                    """, (rs, row) -> new Endpoint(rs.getString("id"), rs.getString("secret_encrypted"),
                    readEvents(rs.getString("events_json"))), event.organizationId());
            for (Endpoint endpoint : endpoints) {
                if (!endpoint.events().contains("*") && !endpoint.events().contains(event.type())) continue;
                db.update("""
                        INSERT INTO webhook_delivery
                        (id,organization_id,endpoint_id,event_id,event_type,status,next_attempt_at)
                        VALUES(?,?,?,?,?,'PENDING',?)
                        ON DUPLICATE KEY UPDATE event_id=VALUES(event_id)
                        """, UUID.randomUUID().toString(), event.organizationId(), endpoint.id(),
                        event.id(), event.type(), java.sql.Timestamp.from(clock.instant()));
            }
        }
        db.update("INSERT INTO webhook_event_receipt(event_id,processed_at) VALUES(?,?)",
                event.id(), java.sql.Timestamp.from(clock.instant()));
    }

    private void deliver(String id) {
        int claimed = db.update("""
                UPDATE webhook_delivery SET status='RUNNING',attempt_count=attempt_count+1,claimed_at=?
                WHERE id=? AND status IN ('PENDING','RETRY') AND attempt_count<?
                    AND (next_attempt_at IS NULL OR next_attempt_at<=?)
                """, java.sql.Timestamp.from(clock.instant()), id, MAX_ATTEMPTS, java.sql.Timestamp.from(clock.instant()));
        if (claimed == 0) return;
        List<Delivery> rows = db.query("""
                SELECT d.id,d.organization_id,d.event_id,d.event_type,d.attempt_count,
                       o.schema_version,o.aggregate_type,o.aggregate_id,o.occurred_at,o.payload,
                       e.target_url,e.secret_encrypted,e.active
                FROM webhook_delivery d JOIN outbox_event o ON o.id=d.event_id
                JOIN webhook_endpoint e ON e.id=d.endpoint_id
                WHERE d.id=?
                """, (rs, row) -> new Delivery(rs.getString("id"), rs.getString("organization_id"),
                rs.getString("event_id"), rs.getString("event_type"), rs.getInt("attempt_count"),
                rs.getInt("schema_version"), rs.getString("aggregate_type"), rs.getString("aggregate_id"),
                rs.getTimestamp("occurred_at").toInstant(), parse(rs.getString("payload")),
                rs.getString("target_url"), rs.getString("secret_encrypted"), rs.getBoolean("active")), id);
        if (rows.isEmpty() || !rows.getFirst().active()) {
            finish(id, "CANCELLED", null, "Webhook endpoint is unavailable");
            return;
        }
        Delivery delivery = rows.getFirst();
        try {
            URI uri = URI.create(delivery.url());
            ensurePublicAddress(uri);
            String body = json.writeValueAsString(Map.of(
                    "id", delivery.eventId(),
                    "type", delivery.eventType(),
                    "schemaVersion", delivery.schemaVersion(),
                    "aggregateType", delivery.aggregateType(),
                    "aggregateId", delivery.aggregateId(),
                    "occurredAt", delivery.occurredAt(),
                    "data", sanitize(delivery.payload())));
            String secret = cipher.decrypt(delivery.encryptedSecret());
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Event", delivery.eventType())
                    .header("X-Webhook-Delivery", delivery.id())
                    .header("X-Webhook-Signature", signature(secret, body))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                recordAttempt(delivery, response.statusCode(), null);
                finish(delivery.id(), "SUCCEEDED", response.statusCode(), null);
            } else {
                recordFailure(delivery.id(), response.statusCode(), "HTTP_STATUS",
                        "Endpoint returned HTTP " + response.statusCode(), delivery.attempt());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(delivery.id(), null, "INTERRUPTED", "Delivery was interrupted", delivery.attempt());
        } catch (Exception exception) {
            String message = exception instanceof java.net.ConnectException ? "Connection failed"
                    : exception instanceof java.net.http.HttpTimeoutException ? "Delivery timed out"
                    : exception instanceof SecurityException ? "Endpoint address is not public"
                    : exception.getClass().getSimpleName();
            recordFailure(delivery.id(), null, "DELIVERY_FAILED", message, delivery.attempt());
        }
    }

    private void recordFailure(String id, Integer statusCode, String errorCode, String message) {
        List<Integer> attempts = db.queryForList("SELECT attempt_count FROM webhook_delivery WHERE id=?", Integer.class, id);
        if (attempts.isEmpty()) return;
        recordFailure(id, statusCode, errorCode, message, attempts.getFirst());
    }

    private void recordFailure(String id, Integer statusCode, String errorCode, String message, int attempt) {
        String safeMessage = message == null ? errorCode : message.substring(0, Math.min(message.length(), 500));
        db.update("""
                INSERT INTO webhook_delivery_attempt(id,delivery_id,attempt_number,status_code,error_message,attempted_at)
                VALUES(?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), id, attempt, statusCode,
                errorCode + ": " + safeMessage, java.sql.Timestamp.from(clock.instant()));
        if (attempt >= MAX_ATTEMPTS) {
            finish(id, "FAILED", statusCode, safeMessage);
            return;
        }
        long delaySeconds = retryDelaySeconds(attempt);
        db.update("""
                UPDATE webhook_delivery SET status='RETRY',claimed_at=NULL,next_attempt_at=?,last_status_code=?,last_error=?
                WHERE id=?
                """, java.sql.Timestamp.from(clock.instant().plusSeconds(delaySeconds)),
                statusCode, safeMessage, id);
    }

    private void recordAttempt(Delivery delivery, Integer statusCode, String error) {
        db.update("""
                INSERT INTO webhook_delivery_attempt(id,delivery_id,attempt_number,status_code,error_message,attempted_at)
                VALUES(?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), delivery.id(), delivery.attempt(), statusCode,
                error, java.sql.Timestamp.from(clock.instant()));
    }

    private void finish(String id, String status, Integer statusCode, String error) {
        db.update("""
                UPDATE webhook_delivery SET status=?,claimed_at=NULL,last_status_code=?,last_error=?,completed_at=?,next_attempt_at=NULL
                WHERE id=?
                """, status, statusCode, error, java.sql.Timestamp.from(clock.instant()), id);
    }

    static long retryDelaySeconds(int attempt) {
        if (attempt < 1) throw new IllegalArgumentException("Attempt number must be positive");
        return Math.min(3600, 30L << Math.min(attempt - 1, 7));
    }

    private void ensurePublicAddress(URI uri) throws Exception {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) throw new SecurityException("Invalid webhook URL");
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            byte[] bytes = address.getAddress();
            boolean uniqueLocalIpv6 = address instanceof Inet6Address && (bytes[0] & 0xFE) == 0xFC;
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress() || uniqueLocalIpv6) {
                throw new SecurityException("Webhook URL resolves to a private address");
            }
        }
    }

    private String signature(String secret, String body) throws Exception {
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + java.util.HexFormat.of().formatHex(
                mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private Object sanitize(Object value) {
        if (value instanceof Map<?, ?> source) {
            var clean = new LinkedHashMap<String, Object>();
            source.forEach((key, nested) -> {
                String normalized = String.valueOf(key).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
                if (PRIVATE_FIELDS.stream().noneMatch(normalized::contains)) clean.put(String.valueOf(key), sanitize(nested));
            });
            return clean;
        }
        if (value instanceof List<?> source) return source.stream().map(this::sanitize).toList();
        return value;
    }

    private Map<String, Object> parse(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Outbox event payload is invalid JSON", exception);
        }
    }

    private List<String> readEvents(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Webhook event subscriptions are invalid JSON", exception);
        }
    }

    private record OutboxEvent(String id, String organizationId, String type, int schemaVersion,
                               String aggregateType, String aggregateId, Instant occurredAt,
                               Map<String, Object> payload) {}

    private record Endpoint(String id, String encryptedSecret, List<String> events) {}

    private record Delivery(String id, String organizationId, String eventId, String eventType,
                            int attempt, int schemaVersion, String aggregateType, String aggregateId,
                            Instant occurredAt, Map<String, Object> payload, String url,
                            String encryptedSecret, boolean active) {}
}
