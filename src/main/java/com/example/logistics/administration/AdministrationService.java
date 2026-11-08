package com.example.logistics.administration;

import com.example.logistics.identity.application.SecretCipher;
import com.example.logistics.shared.api.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdministrationService {
    private static final Set<String> PRIVATE_AUDIT_FIELDS = Set.of(
            "password", "passwordhash", "token", "secret", "authorization", "email", "phone",
            "address", "ipaddress", "totpsecret", "recoverycode");

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final SecretCipher cipher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, SettingValue> settingCache = new ConcurrentHashMap<>();

    public AdministrationService(JdbcTemplate db, ObjectMapper json, SecretCipher cipher, Clock clock) {
        this.db = db;
        this.json = json;
        this.cipher = cipher;
        this.clock = clock;
    }

    @Transactional
    public void recordAudit(UUID organizationId, UUID actorId, String ipAddress, String action,
                            String entity, UUID entityId, Object before, Object after, boolean securityEvent) {
        db.update("""
                INSERT INTO audit_log
                (id,organization_id,actor_id,ip_address,action,resource_type,resource_id,occurred_at,
                 before_json,after_json,security_event,change_summary)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), organizationId == null ? null : organizationId.toString(),
                actorId == null ? null : actorId.toString(), truncate(ipAddress, 64), action, entity,
                entityId == null ? null : entityId.toString(), java.sql.Timestamp.from(clock.instant()),
                toJson(sanitize(before)), toJson(sanitize(after)), securityEvent, action);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchAudit(UUID organizationId, UUID actorId, String action,
                                                  String entity, Boolean securityEvent,
                                                  Instant from, Instant to, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,actor_id actorId,ip_address ipAddress,action,resource_type entity,
                       resource_id entityId,occurred_at occurredAt,before_json beforeJson,
                       after_json afterJson,security_event securityEvent
                FROM audit_log WHERE organization_id=?
                """);
        List<Object> args = new ArrayList<>();
        args.add(organizationId.toString());
        if (actorId != null) { sql.append(" AND actor_id=?"); args.add(actorId.toString()); }
        if (action != null && !action.isBlank()) { sql.append(" AND action=?"); args.add(action.trim()); }
        if (entity != null && !entity.isBlank()) { sql.append(" AND resource_type=?"); args.add(entity.trim()); }
        if (securityEvent != null) { sql.append(" AND security_event=?"); args.add(securityEvent); }
        if (from != null) { sql.append(" AND occurred_at>=?"); args.add(java.sql.Timestamp.from(from)); }
        if (to != null) { sql.append(" AND occurred_at<?"); args.add(java.sql.Timestamp.from(to)); }
        sql.append(" ORDER BY occurred_at DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 500)));
        return db.queryForList(sql.toString(), args.toArray()).stream().<Map<String, Object>>map(row -> {
            var result = new LinkedHashMap<String, Object>(row);
            result.put("before", parseJson((String) row.remove("beforeJson")));
            result.put("after", parseJson((String) row.remove("afterJson")));
            result.remove("beforeJson");
            result.remove("afterJson");
            return result;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> settings(UUID organizationId) {
        return db.queryForList("""
                SELECT setting_key settingKey,value_type valueType,is_secret isSecret,updated_at updatedAt
                FROM setting WHERE scope_id=? ORDER BY setting_key
                """, organizationId.toString());
    }

    @Transactional
    public Map<String, Object> putSetting(UUID organizationId, UUID actorId, String ipAddress,
                                         String key, String type, String value, boolean secret) {
        validateSettingKey(key);
        String normalizedType = type.toUpperCase(Locale.ROOT);
        validateSettingValue(normalizedType, value);
        String storedValue = secret ? cipher.encrypt(value) : value;
        db.update("""
                INSERT INTO setting(id,scope_id,setting_key,setting_value,value_type,is_secret)
                VALUES(?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value),value_type=VALUES(value_type),
                    is_secret=VALUES(is_secret),updated_at=CURRENT_TIMESTAMP(6),version=version+1
                """, UUID.randomUUID().toString(), organizationId.toString(), key, storedValue, normalizedType, secret);
        settingCache.remove(cacheKey(organizationId, key));
        recordAudit(organizationId, actorId, ipAddress, "SETTING_UPDATED", "setting", null, null,
                Map.of("key", key, "type", normalizedType, "secret", secret), false);
        return Map.of("key", key, "type", normalizedType, "secret", secret, "updated", true);
    }

    @Transactional(readOnly = true)
    public SettingValue setting(UUID organizationId, String key) {
        validateSettingKey(key);
        String cacheKey = cacheKey(organizationId, key);
        return settingCache.computeIfAbsent(cacheKey, ignored -> db.query("""
                SELECT setting_value,value_type,is_secret FROM setting WHERE scope_id=? AND setting_key=?
                """, rs -> {
            if (!rs.next()) return null;
            boolean secret = rs.getBoolean(3);
            String value = rs.getString(1);
            return new SettingValue(secret ? cipher.decrypt(value) : value, rs.getString(2), secret);
        }, organizationId.toString(), key));
    }

    @Transactional
    public Map<String, Object> nextSequence(UUID organizationId, String key, String prefix) {
        if (key == null || !key.matches("[A-Za-z0-9._-]{1,100}")) throw invalid("Sequence key is invalid");
        String cleanPrefix = prefix == null ? "" : prefix.trim();
        if (cleanPrefix.length() > 30) throw invalid("Sequence prefix cannot exceed 30 characters");
        String scope = organizationId.toString();
        db.update("""
                INSERT INTO number_sequence(id,scope_id,sequence_key,prefix,current_value)
                VALUES(?,?,?,?,0) ON DUPLICATE KEY UPDATE sequence_key=VALUES(sequence_key)
                """, UUID.randomUUID().toString(), scope, key, cleanPrefix);
        db.update("UPDATE number_sequence SET current_value=LAST_INSERT_ID(current_value+1) WHERE scope_id=? AND sequence_key=?",
                scope, key);
        Long value = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        String storedPrefix = db.queryForObject("""
                SELECT prefix FROM number_sequence WHERE scope_id=? AND sequence_key=?
                """, String.class, scope, key);
        return Map.of("key", key, "value", value, "number", storedPrefix + value);
    }

    @Transactional
    public CreatedApiKey createApiKey(UUID organizationId, UUID actorId, String ipAddress,
                                      String name, List<String> scopes, Instant expiresAt) {
        if (name == null || name.isBlank() || name.trim().length() > 120) throw invalid("API key name is required and must be at most 120 characters");
        if (scopes == null || scopes.isEmpty() || scopes.size() > 30
                || scopes.stream().anyMatch(scope -> scope == null || !scope.matches("[a-z][a-z0-9._:-]{1,119}"))) {
            throw invalid("One to 30 valid API key scopes are required");
        }
        if (expiresAt != null && !expiresAt.isAfter(clock.instant())) throw invalid("API key expiration must be in the future");
        String secret = randomSecret("lcp_");
        UUID id = UUID.randomUUID();
        db.update("""
                INSERT INTO api_key(id,organization_id,created_by,name,key_prefix,key_hash,scopes_json,expires_at)
                VALUES(?,?,?,?,?,?,?,?)
                """, id.toString(), organizationId.toString(), actorId.toString(), name.trim(),
                secret.substring(0, 12), sha256(secret), toJson(scopes),
                expiresAt == null ? null : java.sql.Timestamp.from(expiresAt));
        recordAudit(organizationId, actorId, ipAddress, "API_KEY_CREATED", "api_key", id, null,
                Map.of("name", name.trim(), "scopes", scopes), true);
        return new CreatedApiKey(id, secret, secret.substring(0, 12), scopes, expiresAt);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> apiKeys(UUID organizationId) {
        return db.queryForList("""
                SELECT id,name,key_prefix keyPrefix,scopes_json scopes,expires_at expiresAt,
                       revoked_at revokedAt,last_used_at lastUsedAt,created_at createdAt
                FROM api_key WHERE organization_id=? ORDER BY created_at DESC
                """, organizationId.toString()).stream().map(row -> {
            row.put("scopes", parseJson((String) row.get("scopes")));
            return row;
        }).toList();
    }

    @Transactional
    public void revokeApiKey(UUID organizationId, UUID actorId, String ipAddress, UUID id) {
        int changed = db.update("UPDATE api_key SET revoked_at=? WHERE id=? AND organization_id=? AND revoked_at IS NULL",
                java.sql.Timestamp.from(clock.instant()), id.toString(), organizationId.toString());
        if (changed == 0) throw missing("API key");
        recordAudit(organizationId, actorId, ipAddress, "API_KEY_REVOKED", "api_key", id, null, Map.of("revoked", true), true);
    }

    @Transactional
    public ApiKeyIdentity authenticateApiKey(String presentedKey, String requiredScope, String ipAddress) {
        if (presentedKey == null || presentedKey.length() > 256) return null;
        List<ApiKeyIdentity> keys = db.query("""
                SELECT id,organization_id,scopes_json,expires_at FROM api_key
                WHERE key_hash=? AND revoked_at IS NULL
                """, (rs, row) -> new ApiKeyIdentity(UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("organization_id")), readScopes(rs.getString("scopes_json")),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant()),
                sha256(presentedKey));
        if (keys.isEmpty()) return null;
        ApiKeyIdentity key = keys.getFirst();
        if (key.expiresAt() != null && !key.expiresAt().isAfter(clock.instant())
                || requiredScope != null && !key.scopes().contains(requiredScope)) return null;
        db.update("UPDATE api_key SET last_used_at=? WHERE id=?", java.sql.Timestamp.from(clock.instant()), key.id().toString());
        recordAudit(key.organizationId(), key.id(), ipAddress, "API_KEY_USED", "api_key", key.id(),
                null, Map.of("scope", requiredScope == null ? "request" : requiredScope), true);
        return key;
    }

    @Transactional
    public CreatedWebhook createWebhook(UUID organizationId, UUID actorId, String ipAddress, String name,
                                        String targetUrl, List<String> events) {
        validateWebhook(name, targetUrl, events);
        UUID id = UUID.randomUUID();
        String secret = randomSecret("whsec_");
        db.update("""
                INSERT INTO webhook_endpoint(id,organization_id,created_by,name,target_url,secret_encrypted,events_json)
                VALUES(?,?,?,?,?,?,?)
                """, id.toString(), organizationId.toString(), actorId.toString(), name.trim(), targetUrl,
                cipher.encrypt(secret), toJson(events.stream().distinct().toList()));
        recordAudit(organizationId, actorId, ipAddress, "WEBHOOK_CREATED", "webhook", id, null,
                Map.of("name", name.trim(), "events", events), true);
        return new CreatedWebhook(id, secret);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> webhooks(UUID organizationId) {
        return db.queryForList("""
                SELECT id,name,target_url targetUrl,events_json events,active,created_at createdAt
                FROM webhook_endpoint WHERE organization_id=? ORDER BY created_at DESC
                """, organizationId.toString()).stream().map(row -> {
            row.put("events", parseJson((String) row.get("events")));
            return row;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> webhookDeliveries(UUID organizationId, UUID endpointId, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.id,d.endpoint_id endpointId,d.event_id eventId,d.event_type eventType,d.status,
                       d.attempt_count attemptCount,d.next_attempt_at nextAttemptAt,
                       d.last_status_code lastStatusCode,d.last_error lastError,d.created_at createdAt,
                       d.completed_at completedAt
                FROM webhook_delivery d JOIN webhook_endpoint e ON e.id=d.endpoint_id
                WHERE d.organization_id=?
                """);
        List<Object> args = new ArrayList<>();
        args.add(organizationId.toString());
        if (endpointId != null) {
            sql.append(" AND d.endpoint_id=?");
            args.add(endpointId.toString());
        }
        sql.append(" ORDER BY d.created_at DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 500)));
        return db.queryForList(sql.toString(), args.toArray());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> webhookAttempts(UUID organizationId, UUID deliveryId) {
        return db.queryForList("""
                SELECT a.attempt_number attemptNumber,a.status_code statusCode,a.error_message errorMessage,
                       a.attempted_at attemptedAt
                FROM webhook_delivery_attempt a JOIN webhook_delivery d ON d.id=a.delivery_id
                WHERE d.id=? AND d.organization_id=? ORDER BY a.attempt_number
                """, deliveryId.toString(), organizationId.toString());
    }

    @Transactional
    public Map<String, Object> putCalendar(UUID organizationId, String key, String name, String locale,
                                          String timezone, List<Integer> weekendDays) {
        if (key == null || !key.matches("[A-Za-z0-9._-]{1,80}") || name == null || name.isBlank()
                || name.trim().length() > 120 || locale == null || !locale.matches("[a-z]{2}(-[A-Z]{2})?")) {
            throw invalid("Calendar key, name, or locale is invalid");
        }
        if (weekendDays == null || weekendDays.size() > 7
                || weekendDays.stream().anyMatch(day -> day == null || day < 0 || day > 6)) {
            throw invalid("Weekend days must be unique values from 0 to 6");
        }
        String zone;
        try {
            zone = java.time.ZoneId.of(timezone).getId();
        } catch (Exception exception) {
            throw invalid("Calendar timezone is invalid");
        }
        String days = weekendDays.stream().distinct().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        db.update("""
                INSERT INTO business_calendar(id,organization_id,calendar_key,name,locale,timezone,weekend_days)
                VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),locale=VALUES(locale),
                    timezone=VALUES(timezone),weekend_days=VALUES(weekend_days),updated_at=CURRENT_TIMESTAMP(6)
                """, UUID.randomUUID().toString(), organizationId.toString(), key, name.trim(), locale, zone, days);
        return db.queryForMap("""
                SELECT id,calendar_key calendarKey,name,locale,timezone,weekend_days weekendDays
                FROM business_calendar WHERE organization_id=? AND calendar_key=?
                """, organizationId.toString(), key);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> calendars(UUID organizationId) {
        return db.queryForList("""
                SELECT id,calendar_key calendarKey,name,locale,timezone,weekend_days weekendDays
                FROM business_calendar WHERE organization_id=? ORDER BY calendar_key
                """, organizationId.toString());
    }

    @Transactional
    public Map<String, Object> putHoliday(UUID organizationId, String calendarKey,
                                         java.time.LocalDate date, String name) {
        if (name == null || name.isBlank() || name.trim().length() > 120) throw invalid("Holiday name is required and must be at most 120 characters");
        if (db.queryForObject("SELECT COUNT(*) FROM business_calendar WHERE organization_id=? AND calendar_key=?",
                Integer.class, organizationId.toString(), calendarKey) == 0) throw missing("Business calendar");
        db.update("""
                INSERT INTO business_holiday(id,organization_id,calendar_key,holiday_date,name)
                VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name)
                """, UUID.randomUUID().toString(), organizationId.toString(), calendarKey,
                java.sql.Date.valueOf(date), name.trim());
        return Map.of("calendarKey", calendarKey, "date", date, "name", name.trim());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> holidays(UUID organizationId, String calendarKey,
                                               java.time.LocalDate from, java.time.LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) throw invalid("Holiday end date must not precede its start date");
        StringBuilder sql = new StringBuilder("""
                SELECT calendar_key calendarKey,holiday_date holidayDate,name
                FROM business_holiday WHERE organization_id=?
                """);
        List<Object> args = new ArrayList<>();
        args.add(organizationId.toString());
        if (calendarKey != null) { sql.append(" AND calendar_key=?"); args.add(calendarKey); }
        if (from != null) { sql.append(" AND holiday_date>=?"); args.add(java.sql.Date.valueOf(from)); }
        if (to != null) { sql.append(" AND holiday_date<=?"); args.add(java.sql.Date.valueOf(to)); }
        sql.append(" ORDER BY holiday_date LIMIT 500");
        return db.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public CreatedWebhook rotateWebhookSecret(UUID organizationId, UUID actorId, String ipAddress, UUID id) {
        String secret = randomSecret("whsec_");
        int changed = db.update("UPDATE webhook_endpoint SET secret_encrypted=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND organization_id=? AND active=TRUE",
                cipher.encrypt(secret), id.toString(), organizationId.toString());
        if (changed == 0) throw missing("Webhook");
        recordAudit(organizationId, actorId, ipAddress, "WEBHOOK_SECRET_ROTATED", "webhook", id, null,
                Map.of("secretRotated", true), true);
        return new CreatedWebhook(id, secret);
    }

    @Transactional
    public void updateWebhook(UUID organizationId, UUID actorId, String ipAddress, UUID id,
                              String name, String targetUrl, List<String> events) {
        validateWebhook(name, targetUrl, events);
        int changed = db.update("""
                UPDATE webhook_endpoint SET name=?,target_url=?,events_json=?,updated_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND organization_id=? AND active=TRUE
                """, name.trim(), targetUrl, toJson(events.stream().distinct().toList()),
                id.toString(), organizationId.toString());
        if (changed == 0) throw missing("Webhook");
        recordAudit(organizationId, actorId, ipAddress, "WEBHOOK_UPDATED", "webhook", id, null,
                Map.of("name", name.trim(), "events", events), true);
    }

    @Transactional
    public void revokeWebhook(UUID organizationId, UUID actorId, String ipAddress, UUID id) {
        int changed = db.update("UPDATE webhook_endpoint SET active=FALSE,updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND organization_id=? AND active=TRUE",
                id.toString(), organizationId.toString());
        if (changed == 0) throw missing("Webhook");
        recordAudit(organizationId, actorId, ipAddress, "WEBHOOK_REVOKED", "webhook", id, null,
                Map.of("active", false), true);
        db.update("""
                UPDATE webhook_delivery SET status='CANCELLED',completed_at=?
                WHERE endpoint_id=? AND organization_id=? AND status IN ('PENDING','RETRY')
                """, java.sql.Timestamp.from(clock.instant()), id.toString(), organizationId.toString());
    }

    public String webhookSignature(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign webhook payload", exception);
        }
    }

    public boolean verifyWebhookSignature(String secret, String body, String signature) {
        if (secret == null || body == null || signature == null) return false;
        byte[] expected = webhookSignature(secret, body).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
    }

    private void validateWebhook(String name, String targetUrl, List<String> events) {
        if (name == null || name.isBlank() || name.trim().length() > 120) throw invalid("Webhook name is required and must be at most 120 characters");
        java.net.URI uri;
        try {
            uri = java.net.URI.create(targetUrl);
        } catch (IllegalArgumentException exception) {
            throw invalid("Webhook URL must be a valid HTTPS URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || targetUrl.length() > 2048) {
            throw invalid("Webhook URL must be a valid HTTPS URL without user information, query parameters, or fragments");
        }
        if (events == null || events.isEmpty() || events.size() > 50
                || events.stream().anyMatch(event -> event == null || !event.matches("[A-Za-z][A-Za-z0-9.]{0,199}"))) {
            throw invalid("One to 50 valid webhook event subscriptions are required");
        }
    }

    private void validateSettingKey(String key) {
        if (key == null || !key.matches("[a-zA-Z][a-zA-Z0-9._-]{1,149}")) throw invalid("Setting key is invalid");
    }

    private void validateSettingValue(String type, String value) {
        if (value == null || value.length() > 8192) throw invalid("Setting value is required and must not exceed 8192 characters");
        try {
            switch (type) {
                case "STRING" -> { }
                case "INTEGER" -> Long.parseLong(value);
                case "DECIMAL" -> new BigDecimal(value);
                case "BOOLEAN" -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw invalid("Boolean setting must be true or false");
                }
                case "JSON" -> json.readTree(value);
                default -> throw invalid("Setting type must be STRING, INTEGER, DECIMAL, BOOLEAN, or JSON");
            }
        } catch (NumberFormatException | java.io.IOException exception) {
            throw invalid("Setting value does not match its declared type");
        }
    }

    private Object sanitize(Object value) {
        if (value instanceof Map<?, ?> source) {
            var clean = new LinkedHashMap<String, Object>();
            source.forEach((key, nested) -> {
                String normalized = String.valueOf(key).toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
                if (PRIVATE_AUDIT_FIELDS.stream().noneMatch(normalized::contains)) clean.put(String.valueOf(key), sanitize(nested));
            });
            return clean;
        }
        if (value instanceof List<?> source) return source.stream().map(this::sanitize).toList();
        return value;
    }

    private List<String> readScopes(String value) {
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalStateException("Stored API key scopes are invalid", exception); }
    }

    private Object parseJson(String value) {
        try { return value == null ? null : json.readValue(value, Object.class); }
        catch (Exception exception) { throw new IllegalStateException("Stored administration JSON is invalid", exception); }
    }

    private String toJson(Object value) {
        try { return value == null ? null : json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalArgumentException("Unable to serialize administration data", exception); }
    }

    private String randomSecret(String prefix) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String cacheKey(UUID organizationId, String key) {
        return organizationId + ":" + key;
    }

    private static String truncate(String value, int maxLength) {
        return value == null ? null : value.substring(0, Math.min(value.length(), maxLength));
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "ADMINISTRATION_VALIDATION_FAILED", message);
    }

    private static ApiException missing(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", resource + " was not found");
    }

    public record SettingValue(String value, String type, boolean secret) {}
    public record CreatedApiKey(UUID id, String secret, String prefix, List<String> scopes, Instant expiresAt) {}
    public record ApiKeyIdentity(UUID id, UUID organizationId, List<String> scopes, Instant expiresAt) {}
    public record CreatedWebhook(UUID id, String secret) {}
}
