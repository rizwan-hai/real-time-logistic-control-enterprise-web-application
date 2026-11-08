package com.example.logistics.administration;

import com.example.logistics.shared.api.ApiResponse;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdministrationController {
    private static final String ADMIN = "hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN')";
    private final AdministrationService administration;
    private final JdbcTemplate db;
    private final javax.sql.DataSource dataSource;
    private final RedisConnectionFactory redis;
    private final MinioClient minio;
    private final com.example.logistics.shared.config.MinioProperties minioProperties;

    public AdministrationController(AdministrationService administration, JdbcTemplate db,
                                    javax.sql.DataSource dataSource, RedisConnectionFactory redis,
                                    MinioClient minio,
                                    com.example.logistics.shared.config.MinioProperties minioProperties) {
        this.administration = administration;
        this.db = db;
        this.dataSource = dataSource;
        this.redis = redis;
        this.minio = minio;
        this.minioProperties = minioProperties;
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','AUDITOR')")
    public ApiResponse<List<Map<String, Object>>> audit(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID actorId, @RequestParam(required = false) String action,
            @RequestParam(required = false) String entity, @RequestParam(required = false) Boolean securityEvent,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "100") int limit) {
        if (from != null && to != null && !from.isBefore(to)) throw new IllegalArgumentException("Audit time range must be increasing");
        return ApiResponse.of(administration.searchAudit(organization(jwt), actorId, action, entity,
                securityEvent, from, to, limit));
    }

    @GetMapping(value = "/audit/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','AUDITOR')")
    public ResponseEntity<String> exportAudit(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID actorId, @RequestParam(required = false) String action,
            @RequestParam(required = false) String entity, @RequestParam(required = false) Boolean securityEvent,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "500") int limit) {
        var records = administration.searchAudit(organization(jwt), actorId, action, entity,
                securityEvent, from, to, limit);
        var csv = new StringBuilder("id,actorId,ipAddress,action,entity,entityId,occurredAt,securityEvent,before,after\r\n");
        for (var record : records) {
            csv.append(csv(record.get("id"))).append(',').append(csv(record.get("actorId"))).append(',')
                    .append(csv(record.get("ipAddress"))).append(',').append(csv(record.get("action"))).append(',')
                    .append(csv(record.get("entity"))).append(',').append(csv(record.get("entityId"))).append(',')
                    .append(csv(record.get("occurredAt"))).append(',').append(csv(record.get("securityEvent"))).append(',')
                    .append(csv(record.get("before"))).append(',').append(csv(record.get("after"))).append("\r\n");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
                .body(csv.toString());
    }

    @GetMapping("/settings")
    @PreAuthorize(ADMIN)
    public ApiResponse<List<Map<String, Object>>> settings(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(administration.settings(organization(jwt)));
    }

    @GetMapping("/settings/{key}")
    @PreAuthorize(ADMIN)
    public ApiResponse<Map<String, Object>> setting(@AuthenticationPrincipal Jwt jwt, @PathVariable String key) {
        var setting = administration.setting(organization(jwt), key);
        if (setting == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Setting not found");
        return ApiResponse.of(Map.of("key", key, "type", setting.type(), "secret", setting.secret(),
                "value", setting.secret() ? "" : setting.value()));
    }

    @PutMapping("/settings/{key}")
    @PreAuthorize(ADMIN)
    public ApiResponse<Map<String, Object>> putSetting(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String key, @Valid @RequestBody SettingRequest request) {
        return ApiResponse.of(administration.putSetting(organization(jwt), actor(jwt), clientIp(), key,
                request.type(), request.value(), request.secret()));
    }

    @PostMapping("/sequences/{key}/next")
    @PreAuthorize(ADMIN)
    public ApiResponse<Map<String, Object>> nextSequence(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String key, @RequestParam(defaultValue = "") String prefix) {
        return ApiResponse.of(administration.nextSequence(organization(jwt), key, prefix));
    }

    @GetMapping("/api-keys")
    @PreAuthorize(ADMIN)
    public ApiResponse<List<Map<String, Object>>> apiKeys(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(administration.apiKeys(organization(jwt)));
    }

    @PostMapping("/api-keys")
    @PreAuthorize(ADMIN)
    public ApiResponse<AdministrationService.CreatedApiKey> createApiKey(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateApiKeyRequest request) {
        return ApiResponse.of(administration.createApiKey(organization(jwt), actor(jwt), clientIp(), request.name(),
                request.scopes(), request.expiresAt()));
    }

    @PostMapping("/api-keys/{id}/revoke")
    @PreAuthorize(ADMIN)
    public ApiResponse<Map<String, Boolean>> revokeApiKey(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        administration.revokeApiKey(organization(jwt), actor(jwt), clientIp(), id);
        return ApiResponse.of(Map.of("revoked", true));
    }

    @GetMapping("/webhooks")
    @PreAuthorize(ADMIN)
    public ApiResponse<List<Map<String, Object>>> webhooks(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(administration.webhooks(organization(jwt)));
    }

    @PostMapping("/webhooks")
    @PreAuthorize(ADMIN)
    public ApiResponse<AdministrationService.CreatedWebhook> createWebhook(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateWebhookRequest request) {
        return ApiResponse.of(administration.createWebhook(organization(jwt), actor(jwt), clientIp(),
                request.name(), request.targetUrl(), request.events()));
    }

    @PutMapping("/webhooks/{id}")
    @PreAuthorize(ADMIN)
    public ApiResponse<Map<String, Boolean>> updateWebhook(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id, @Valid @RequestBody CreateWebhookRequest request) {
        administration.updateWebhook(organization(jwt), actor(jwt), clientIp(), id,
                request.name(), request.targetUrl(), request.events());
        return ApiResponse.of(Map.of("updated", true));
    }

    @PostMapping("/webhooks/{id}/rotate-secret")
    @PreAuthorize(ADMIN)
    public ApiResponse<AdministrationService.CreatedWebhook> rotateWebhookSecret(@AuthenticationPrincipal Jwt jwt,
                                                                                  @PathVariable UUID id) {
        return ApiResponse.of(administration.rotateWebhookSecret(organization(jwt), actor(jwt), clientIp(), id));
    }

    @PostMapping("/webhooks/{id}/revoke")
    @PreAuthorize(ADMIN)
    public ApiResponse<Map<String, Boolean>> revokeWebhook(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        administration.revokeWebhook(organization(jwt), actor(jwt), clientIp(), id);
        return ApiResponse.of(Map.of("revoked", true));
    }

    @GetMapping("/webhooks/deliveries")
    @PreAuthorize(ADMIN)
    public ApiResponse<List<Map<String, Object>>> webhookDeliveries(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID endpointId, @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.of(administration.webhookDeliveries(organization(jwt), endpointId, limit));
    }

    @GetMapping("/webhooks/deliveries/{deliveryId}/attempts")
    @PreAuthorize(ADMIN)
    public ApiResponse<List<Map<String, Object>>> webhookAttempts(@AuthenticationPrincipal Jwt jwt,
                                                                    @PathVariable UUID deliveryId) {
        return ApiResponse.of(administration.webhookAttempts(organization(jwt), deliveryId));
    }

    @GetMapping("/calendars")
    @PreAuthorize(ADMIN)
    public ApiResponse<List<Map<String, Object>>> calendars(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(administration.calendars(organization(jwt)));
    }

    @PutMapping("/calendars/{key}")
    @PreAuthorize(ADMIN)
    public ApiResponse<Map<String, Object>> putCalendar(@AuthenticationPrincipal Jwt jwt, @PathVariable String key,
            @Valid @RequestBody CalendarRequest request) {
        return ApiResponse.of(administration.putCalendar(organization(jwt), key, request.name(),
                request.locale(), request.timezone(), request.weekendDays()));
    }

    @GetMapping("/holidays")
    @PreAuthorize(ADMIN)
    public ApiResponse<List<Map<String, Object>>> holidays(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String calendarKey, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return ApiResponse.of(administration.holidays(organization(jwt), calendarKey, from, to));
    }

    @PutMapping("/holidays/{calendarKey}/{date}")
    @PreAuthorize(ADMIN)
    public ApiResponse<Map<String, Object>> putHoliday(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String calendarKey, @PathVariable LocalDate date, @Valid @RequestBody HolidayRequest request) {
        return ApiResponse.of(administration.putHoliday(organization(jwt), calendarKey, date, request.name()));
    }

    @PostMapping("/customer-documents")
    @PreAuthorize(ADMIN)
    @Transactional
    public ApiResponse<Map<String, Object>> registerCustomerDocument(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CustomerDocumentRequest request) {
        UUID organizationId = organization(jwt);
        UUID customerId = request.customerId();
        if (db.queryForObject("""
                SELECT COUNT(*) FROM master_data_record
                WHERE id=? AND organization_id=? AND resource_type='customers' AND status='ACTIVE'
                """, Integer.class, customerId.toString(), organizationId.toString()) == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Customer not found");
        }
        String type = request.documentType().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("INVOICE", "POD").contains(type)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Document type must be INVOICE or POD");
        }
        if (type.equals("POD")) {
            if (request.shipmentId() == null || db.queryForObject("""
                    SELECT COUNT(*) FROM shipment s JOIN shipment_order so ON so.shipment_id=s.id
                    JOIN sales_order o ON o.id=so.order_id
                    WHERE s.id=? AND s.organization_id=? AND o.customer_id=?
                    """, Integer.class, request.shipmentId().toString(), organizationId.toString(), customerId.toString()) == 0) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Customer shipment not found");
            }
        }
        int cleanAttachment = db.queryForObject("""
                SELECT COUNT(*) FROM attachment
                WHERE id=? AND organization_id=? AND scan_status='CLEAN' AND UPPER(owner_type)=?
                """, Integer.class, request.attachmentId().toString(), organizationId.toString(), type);
        if (cleanAttachment == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Clean customer document attachment not found");
        }
        UUID documentId = UUID.randomUUID();
        db.update("""
                INSERT INTO customer_document
                (id,organization_id,customer_id,attachment_id,document_type,document_number,shipment_id)
                VALUES(?,?,?,?,?,?,?)
                """, documentId.toString(), organizationId.toString(), customerId.toString(),
                request.attachmentId().toString(), type, request.documentNumber(),
                request.shipmentId() == null ? null : request.shipmentId().toString());
        administration.recordAudit(organizationId, actor(jwt), clientIp(), "CUSTOMER_DOCUMENT_REGISTERED",
                "customer_document", documentId, null, Map.of("documentType", type, "customerId", customerId), false);
        return ApiResponse.of(Map.of("id", documentId, "documentType", type, "registered", true));
    }

    @GetMapping("/health")
    @PreAuthorize(ADMIN)
    public ApiResponse<Map<String, Object>> health() {
        var checks = new LinkedHashMap<String, Object>();
        checks.put("database", check(() -> {
            try (Connection connection = dataSource.getConnection()) {
                if (!connection.isValid(2)) throw new IllegalStateException("Connection is not valid");
            }
        }));
        checks.put("redis", check(() -> {
            try (var connection = redis.getConnection()) {
                if (!"PONG".equalsIgnoreCase(connection.ping())) throw new IllegalStateException("Ping did not return PONG");
            }
        }));
        checks.put("minio", check(() -> {
            if (!minio.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.bucket()).build())) {
                throw new IllegalStateException("Configured bucket does not exist");
            }
        }));
        checks.put("backgroundJobs", Map.of("status", "UP", "pendingOutboxEvents",
                db.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE published_at IS NULL", Long.class)));
        checks.put("gpsIngestion", Map.of("status", "UP", "positionsLastHour",
                db.queryForObject("SELECT COUNT(*) FROM gps_position WHERE recorded_at>=?", Long.class,
                        java.sql.Timestamp.from(Instant.now().minusSeconds(3600)))));
        checks.put("websocket", Map.of("status", "CONFIGURED"));
        checks.put("integrations", Map.of("redis", checks.get("redis"), "minio", checks.get("minio")));
        boolean healthy = checks.values().stream().filter(Map.class::isInstance)
                .map(Map.class::cast).noneMatch(value -> "DOWN".equals(value.get("status")));
        return ApiResponse.of(Map.of("status", healthy ? "UP" : "DEGRADED", "checks", checks,
                "checkedAt", Instant.now()));
    }

    private static Map<String, String> check(CheckedAction action) {
        try {
            action.run();
            return Map.of("status", "UP");
        } catch (Exception exception) {
            return Map.of("status", "DOWN", "error", exception.getClass().getSimpleName());
        }
    }

    private static String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static UUID organization(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("organizationId"));
    }

    private static UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static String clientIp() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet
                ? servlet.getRequest().getRemoteAddr() : null;
    }

    @FunctionalInterface
    private interface CheckedAction { void run() throws Exception; }

    public record SettingRequest(@NotBlank String type, @NotNull String value, boolean secret) {}
    public record CreateApiKeyRequest(@NotBlank @Size(max = 120) String name,
                                      @NotEmpty @Size(max = 30) List<String> scopes, Instant expiresAt) {}
    public record CreateWebhookRequest(@NotBlank @Size(max = 120) String name,
                                       @NotBlank @Size(max = 2048) String targetUrl,
                                       @NotEmpty @Size(max = 50) List<String> events) {}
    public record CalendarRequest(@NotBlank @Size(max = 120) String name, @NotBlank String locale,
                                  @NotBlank String timezone, @NotNull List<Integer> weekendDays) {}
    public record HolidayRequest(@NotBlank @Size(max = 120) String name) {}
    public record CustomerDocumentRequest(@NotNull UUID customerId, @NotNull UUID attachmentId,
            @NotBlank @Size(max = 16) String documentType, @NotBlank @Size(max = 80) String documentNumber,
            UUID shipmentId) {}
}
