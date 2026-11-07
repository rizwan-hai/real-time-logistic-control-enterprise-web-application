package com.example.logistics.notifications;

import com.example.logistics.shared.api.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Set<String> EVENT_TYPES = Set.of(
            "OrderStatusChanged", "ShipmentAssigned", "TripDispatched", "EtaChanged",
            "DeliveryDelay", "GpsAlert", "VehicleIdle", "GeofenceBreach", "DocumentExpiry",
            "LowInventory", "PodCompleted", "InvoiceIssued", "InvoiceOverdue", "PaymentReceived",
            "ShipmentStopStatusChanged");
    private static final List<String> CHANNELS = List.of("EMAIL", "SMS", "WEB_PUSH", "WEBSOCKET");
    private static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final NotificationTemplateRenderer templates;
    private final NotificationProviderRegistry providers;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public NotificationService(JdbcTemplate db, ObjectMapper json, NotificationTemplateRenderer templates,
                               NotificationProviderRegistry providers, Clock clock,
                               PlatformTransactionManager transactionManager) {
        this.db = db;
        this.json = json;
        this.templates = templates;
        this.providers = providers;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.notifications.outbox-poll-millis:2000}")
    public void consumeOutbox() {
        List<String> eventIds = db.queryForList("""
                SELECT e.id FROM outbox_event e
                WHERE NOT EXISTS (SELECT 1 FROM notification_event_receipt r WHERE r.outbox_event_id=e.id)
                ORDER BY e.occurred_at LIMIT 50
                """, String.class);
        for (String eventId : eventIds) {
            try {
                transactions.execute(status -> {
                    consumeOutboxEvent(eventId);
                    return null;
                });
            } catch (Exception ex) {
                recordOutboxFailure(eventId, ex);
            }
        }
    }

    private void consumeOutboxEvent(String eventId) {
        List<OutboxRow> rows = db.query("""
                SELECT e.id,e.organization_id,e.event_type,e.aggregate_type,e.aggregate_id,e.payload
                FROM outbox_event e WHERE e.id=? FOR UPDATE
                """, (rs, row) -> new OutboxRow(rs.getString("id"), rs.getString("organization_id"),
                rs.getString("event_type"), rs.getString("aggregate_type"), rs.getString("aggregate_id"),
                rs.getString("payload")), eventId);
        if (rows.isEmpty()) return;
        OutboxRow event = rows.get(0);
        if (db.queryForObject("SELECT COUNT(*) FROM notification_event_receipt WHERE outbox_event_id=?",
                Integer.class, event.id()) > 0) return;
        try {
            JsonNode payload = json.readTree(event.payload());
            String fingerprint = fingerprint(event, payload);
            boolean duplicate = db.queryForObject(
                    "SELECT COUNT(*) FROM notification_event_receipt WHERE event_fingerprint=?",
                    Integer.class, fingerprint) > 0;
            if (!duplicate && EVENT_TYPES.contains(event.eventType()) && event.organizationId() != null) {
                createNotifications(event, fingerprint, payload);
            }
            db.update("INSERT INTO notification_event_receipt(id,event_fingerprint,outbox_event_id) VALUES(?,?,?)",
                    UUID.randomUUID().toString(), fingerprint, event.id());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to consume notification outbox event", ex);
        }
    }

    private void recordOutboxFailure(String eventId, Exception failure) {
        transactions.executeWithoutResult(status -> {
            List<OutboxRow> rows = db.query("""
                    SELECT id,organization_id,event_type,aggregate_type,aggregate_id,payload
                    FROM outbox_event WHERE id=?
                    """, (rs, row) -> new OutboxRow(rs.getString("id"), rs.getString("organization_id"),
                    rs.getString("event_type"), rs.getString("aggregate_type"), rs.getString("aggregate_id"),
                    rs.getString("payload")), eventId);
            if (rows.isEmpty()) return;
            db.update("UPDATE outbox_event SET attempts=attempts+1,last_error=? WHERE id=?",
                    safeError(failure), eventId);
            int attempts = db.queryForObject("SELECT attempts FROM outbox_event WHERE id=?", Integer.class, eventId);
            log.warn("Notification outbox event {} failed (attempt {})", eventId, attempts, failure);
            if (attempts >= MAX_ATTEMPTS) {
                String fingerprint;
                try { fingerprint = fingerprint(rows.get(0), null); }
                catch (Exception ex) { throw new IllegalStateException("Unable to fingerprint failed outbox event", ex); }
                db.update("INSERT INTO notification_event_receipt(id,event_fingerprint,outbox_event_id) VALUES(?,?,?)",
                        UUID.randomUUID().toString(), fingerprint, eventId);
                log.error("Notification outbox event {} reached its dead-letter attempt limit", eventId);
            }
        });
    }

    @Scheduled(fixedDelayString = "${app.notifications.delivery-poll-millis:2000}")
    @Transactional
    public void deliverPending() {
        Instant now = clock.instant();
        List<DeliveryRow> rows = db.query("""
                SELECT l.id,l.notification_id,l.channel,l.attempts,n.user_id,n.title,n.body,n.deep_link,
                       u.email_normalized,u.phone_normalized
                FROM notification_delivery_log l
                JOIN notification n ON n.id=l.notification_id
                JOIN user_account u ON u.id=n.user_id AND u.organization_id=n.organization_id
                WHERE (l.status IN ('PENDING','RETRY') AND (l.next_attempt_at IS NULL OR l.next_attempt_at<=?))
                   OR (l.status='SENDING' AND l.updated_at<?)
                ORDER BY l.created_at
                LIMIT 50 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new DeliveryRow(rs.getString("id"), rs.getString("notification_id"),
                rs.getString("channel"), rs.getInt("attempts"), rs.getString("user_id"),
                rs.getString("title"), rs.getString("body"), rs.getString("deep_link"),
                rs.getString("email_normalized"), rs.getString("phone_normalized")),
                Timestamp.from(now), Timestamp.from(now.minusSeconds(300)));

        for (DeliveryRow row : rows) {
            db.update("UPDATE notification_delivery_log SET status='SENDING',updated_at=? WHERE id=?",
                    Timestamp.from(now), row.id());
            try {
                providers.provider(row.channel()).deliver(new NotificationDelivery(
                        UUID.fromString(row.notificationId()), UUID.fromString(row.userId()), row.title(),
                        row.body(), row.deepLink(), row.email(), row.phone()));
                db.update("""
                        UPDATE notification_delivery_log
                        SET status='SENT',attempts=attempts+1,next_attempt_at=NULL,delivered_at=?,last_error=NULL,updated_at=?
                        WHERE id=?
                        """, Timestamp.from(now), Timestamp.from(now), row.id());
            } catch (RuntimeException ex) {
                int attempts = row.attempts() + 1;
                if (attempts >= MAX_ATTEMPTS) {
                    db.update("""
                            UPDATE notification_delivery_log
                            SET status='DEAD_LETTER',attempts=?,next_attempt_at=NULL,last_error=?,updated_at=?
                            WHERE id=?
                            """, attempts, safeError(ex), Timestamp.from(now), row.id());
                    log.error("Notification {} delivery over {} reached dead-letter status",
                            row.notificationId(), row.channel(), ex);
                } else {
                    long delaySeconds = Math.min(60L << (attempts - 1), 3600);
                    db.update("""
                            UPDATE notification_delivery_log
                            SET status='RETRY',attempts=?,next_attempt_at=?,last_error=?,updated_at=?
                            WHERE id=?
                            """, attempts, Timestamp.from(now.plusSeconds(delaySeconds)), safeError(ex),
                            Timestamp.from(now), row.id());
                    log.warn("Notification {} delivery over {} failed; retry {} scheduled",
                            row.notificationId(), row.channel(), attempts, ex);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(UUID organizationId, UUID userId, boolean unreadOnly, int page, int size) {
        validatePage(page, size);
        String scope = " WHERE organization_id=? AND user_id=?" + (unreadOnly ? " AND read_at IS NULL" : "");
        List<Map<String, Object>> items = db.queryForList("""
                SELECT id,event_type eventType,title,body,locale,resource_type resourceType,
                       resource_id resourceId,deep_link deepLink,created_at createdAt,read_at readAt
                FROM notification
                """ + scope + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                organizationId.toString(), userId.toString(), size, (long) page * size);
        Integer total = db.queryForObject("SELECT COUNT(*) FROM notification" + scope, Integer.class,
                organizationId.toString(), userId.toString());
        return Map.of("items", items, "page", page, "size", size, "total", total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public int unreadCount(UUID organizationId, UUID userId) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM notification WHERE organization_id=? AND user_id=? AND read_at IS NULL",
                Integer.class, organizationId.toString(), userId.toString());
        return count == null ? 0 : count;
    }

    @Transactional
    public void markRead(UUID organizationId, UUID userId, UUID notificationId) {
        int changed = db.update("""
                UPDATE notification SET read_at=?
                WHERE id=? AND organization_id=? AND user_id=? AND read_at IS NULL
                """, Timestamp.from(clock.instant()), notificationId.toString(), organizationId.toString(), userId.toString());
        if (changed == 0 && db.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE id=? AND organization_id=? AND user_id=?",
                Integer.class, notificationId.toString(), organizationId.toString(), userId.toString()) == 0) {
            throw new NotificationNotFoundException();
        }
    }

    @Transactional
    public int markAllRead(UUID organizationId, UUID userId) {
        return db.update("UPDATE notification SET read_at=? WHERE organization_id=? AND user_id=? AND read_at IS NULL",
                Timestamp.from(clock.instant()), organizationId.toString(), userId.toString());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> preferences(UUID organizationId, UUID userId) {
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT in_app_enabled inApp,email_enabled email,sms_enabled sms,web_push_enabled webPush,locale
                FROM notification_preference WHERE organization_id=? AND user_id=?
                """, organizationId.toString(), userId.toString());
        if (rows.isEmpty()) return Map.of("inApp", true, "email", false, "sms", false, "webPush", false, "locale", "en");
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> updatePreferences(UUID organizationId, UUID userId, PreferenceUpdate preference) {
        if (!Set.of("en", "fa").contains(preference.locale())) {
            throw new IllegalArgumentException("Notification locale must be en or fa");
        }
        db.update("""
                INSERT INTO notification_preference(id,organization_id,user_id,in_app_enabled,email_enabled,sms_enabled,web_push_enabled,locale)
                VALUES(?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE in_app_enabled=VALUES(in_app_enabled),email_enabled=VALUES(email_enabled),
                sms_enabled=VALUES(sms_enabled),web_push_enabled=VALUES(web_push_enabled),locale=VALUES(locale),updated_at=CURRENT_TIMESTAMP(6)
                """, UUID.randomUUID().toString(), organizationId.toString(), userId.toString(), preference.inApp(),
                preference.email(), preference.sms(), preference.webPush(), preference.locale());
        return preferences(organizationId, userId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> deliveryHistory(UUID organizationId, UUID userId, UUID notificationId) {
        if (db.queryForObject("SELECT COUNT(*) FROM notification WHERE id=? AND organization_id=? AND user_id=?",
                Integer.class, notificationId.toString(), organizationId.toString(), userId.toString()) == 0) {
            throw new NotificationNotFoundException();
        }
        return db.queryForList("""
                SELECT channel,status,attempts,delivered_at deliveredAt,created_at createdAt
                FROM notification_delivery_log WHERE notification_id=? ORDER BY created_at,channel
                """, notificationId.toString());
    }

    private void createNotifications(OutboxRow event, String fingerprint, JsonNode payload) throws Exception {
        List<Recipient> recipients = recipients(event.organizationId(), payload);
        for (Recipient recipient : recipients) {
            Map<String, Object> preference = preferenceFor(event.organizationId(), recipient.id());
            String locale = "fa".equals(preference.get("locale")) ? "fa" : "en";
            Map<String, String> values = new LinkedHashMap<>();
            String status = safeStatus(payload == null ? null : payload.path("to").asText(null));
            if (status != null) values.put("status", status);
            values.put("reference", event.aggregateId());
            NotificationTemplateRenderer.Rendered rendered = templates.render(event.eventType(), locale, values);
            String resourceType = safeResourceType(event.aggregateType());
            String deepLink = deepLink(resourceType, event.aggregateId());
            String notificationId = UUID.randomUUID().toString();
            db.update("""
                    INSERT INTO notification(id,organization_id,user_id,source_outbox_id,event_fingerprint,event_type,
                    title,body,locale,resource_type,resource_id,deep_link) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    """, notificationId, event.organizationId(), recipient.id(), event.id(), fingerprint,
                    event.eventType(), rendered.title(), rendered.body(), locale, resourceType,
                    event.aggregateId(), deepLink);

            if (Boolean.TRUE.equals(preference.get("inApp"))) {
                createDelivery(notificationId, "IN_APP");
                createDelivery(notificationId, "WEBSOCKET");
            }
            for (String channel : CHANNELS) {
                if (Boolean.TRUE.equals(preference.get(channelPreference(channel)))) createDelivery(notificationId, channel);
            }
        }
    }

    private List<Recipient> recipients(String organizationId, JsonNode payload) {
        if (payload != null) {
            for (String key : List.of("recipientUserId", "userId")) {
                String target = safeUuid(payload.path(key).asText(null));
                if (target != null) return db.query("""
                        SELECT id,email_normalized email,phone_normalized phone FROM user_account
                        WHERE id=? AND organization_id=? AND active=TRUE
                        """, (rs, row) -> new Recipient(rs.getString("id"), rs.getString("email"), rs.getString("phone")),
                        target, organizationId);
            }
            String driverId = safeUuid(payload.path("driverId").asText(null));
            if (driverId != null) {
                List<Recipient> driverRecipients = db.query("""
                        SELECT id,email_normalized email,phone_normalized phone FROM user_account
                        WHERE driver_id=? AND organization_id=? AND active=TRUE
                        """, (rs, row) -> new Recipient(rs.getString("id"), rs.getString("email"), rs.getString("phone")),
                        driverId, organizationId);
                if (!driverRecipients.isEmpty()) return driverRecipients;
            }
        }
        return db.query("""
                SELECT id,email_normalized email,phone_normalized phone FROM user_account
                WHERE organization_id=? AND active=TRUE ORDER BY id
                """, (rs, row) -> new Recipient(rs.getString("id"), rs.getString("email"), rs.getString("phone")),
                organizationId);
    }

    private Map<String, Object> preferenceFor(String organizationId, String userId) {
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT in_app_enabled inApp,email_enabled email,sms_enabled sms,web_push_enabled webPush,locale
                FROM notification_preference WHERE organization_id=? AND user_id=?
                """, organizationId, userId);
        return rows.isEmpty() ? Map.of("inApp", true, "email", false, "sms", false, "webPush", false, "locale", "en")
                : rows.get(0);
    }

    private void createDelivery(String notificationId, String channel) {
        db.update("""
                INSERT INTO notification_delivery_log(id,notification_id,channel,status,attempts)
                VALUES(?,?,?,'PENDING',0)
                """, UUID.randomUUID().toString(), notificationId, channel);
    }

    private String fingerprint(OutboxRow event, JsonNode payload) throws Exception {
        String content = String.join("|", event.organizationId() == null ? "" : event.organizationId(),
                event.eventType(), event.aggregateType(), event.aggregateId(), payload == null ? event.payload() : payload.toString());
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String safeError(Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        String safe = message.replaceAll("[\\p{Cntrl}]", " ");
        return safe.substring(0, Math.min(safe.length(), 900));
    }

    private String safeStatus(String status) {
        return status != null && status.matches("[A-Za-z0-9_-]{1,40}") ? status.toUpperCase(Locale.ROOT) : null;
    }

    private String safeUuid(String value) {
        if (value == null) return null;
        try { return UUID.fromString(value).toString(); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private String safeResourceType(String aggregateType) {
        return switch (aggregateType == null ? "" : aggregateType.toLowerCase(Locale.ROOT)) {
            case "order" -> "Order";
            case "shipment" -> "Shipment";
            case "trip" -> "Trip";
            case "vehicle" -> "Vehicle";
            case "invoice" -> "Invoice";
            case "payment" -> "Payment";
            default -> "Record";
        };
    }

    private String deepLink(String resourceType, String resourceId) {
        return switch (resourceType) {
            case "Order" -> "/app/staff/orders/" + resourceId;
            case "Shipment" -> "/app/staff/shipments/" + resourceId;
            case "Trip" -> "/app/staff/dispatch";
            case "Vehicle" -> "/app/staff/control-tower";
            default -> "/app/notifications";
        };
    }

    private String channelPreference(String channel) {
        return switch (channel) {
            case "EMAIL" -> "email";
            case "SMS" -> "sms";
            case "WEB_PUSH" -> "webPush";
            default -> "";
        };
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
    }

    private record OutboxRow(String id, String organizationId, String eventType,
                             String aggregateType, String aggregateId, String payload) {}
    private record Recipient(String id, String email, String phone) {}
    private record DeliveryRow(String id, String notificationId, String channel, int attempts,
                               String userId, String title, String body, String deepLink, String email, String phone) {}
    public record PreferenceUpdate(boolean inApp, boolean email, boolean sms, boolean webPush, String locale) {}

    public static class NotificationNotFoundException extends ApiException {
        public NotificationNotFoundException() {
            super(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found");
        }
    }
}
