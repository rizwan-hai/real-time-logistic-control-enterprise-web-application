package com.example.logistics.orders.api;

import com.example.logistics.orders.application.OrderService;
import com.example.logistics.shared.api.ApiResponse;
import com.example.logistics.tracking.TrackingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/v1/portal/orders")
@PreAuthorize("hasRole('CUSTOMER_USER')")
public class CustomerPortalController {
    private final OrderService orders;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final MinioClient minio;
    private final TrackingService tracking;

    public CustomerPortalController(OrderService orders, JdbcTemplate db, ObjectMapper json, MinioClient minio,
                                    TrackingService tracking) {
        this.orders = orders;
        this.db = db;
        this.json = json;
        this.minio = minio;
        this.tracking = tracking;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(orders.listForCustomer(organization(jwt), customer(jwt)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ApiResponse.of(orders.getForCustomer(organization(jwt), customer(jwt), id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.of(orders.createForCustomer(organization(jwt), customer(jwt), actor(jwt),
                request.toOrder()));
    }

    @PostMapping("/bulk")
    @Transactional
    public ApiResponse<List<Map<String, Object>>> bulkCreate(@AuthenticationPrincipal Jwt jwt,
                                                              @Valid @RequestBody BulkOrderRequest request) {
        UUID organizationId = organization(jwt);
        UUID customerId = customer(jwt);
        if (db.queryForObject("SELECT COUNT(*) FROM customer_address WHERE organization_id=? AND customer_id=?",
                Integer.class, organizationId.toString(), customerId.toString()) >= 50) {
            throw invalid("A customer may store at most 50 addresses");
        }
        UUID actorId = actor(jwt);
        return ApiResponse.of(request.orders().stream()
                .map(order -> orders.createForCustomer(organizationId, customerId, actorId, order.toOrder()))
                .toList());
    }

    @GetMapping("/shipments")
    public ApiResponse<List<Map<String, Object>>> shipments(@AuthenticationPrincipal Jwt jwt) {
        UUID organizationId = organization(jwt);
        List<Map<String, Object>> results = db.queryForList("""
                SELECT DISTINCT s.id,s.shipment_number shipmentNumber,s.status,
                       s.created_at createdAt,
                       (SELECT ROUND(g.latitude,3) FROM gps_position g
                        JOIN trip t ON t.id=g.trip_id AND t.organization_id=s.organization_id
                        JOIN trip_shipment tx ON tx.trip_id=t.id AND tx.shipment_id=s.id
                        WHERE g.organization_id=s.organization_id AND g.recorded_at>=?
                        ORDER BY g.recorded_at DESC LIMIT 1) latitude,
                       (SELECT ROUND(g.longitude,3) FROM gps_position g
                        JOIN trip t ON t.id=g.trip_id AND t.organization_id=s.organization_id
                        JOIN trip_shipment tx ON tx.trip_id=t.id AND tx.shipment_id=s.id
                        WHERE g.organization_id=s.organization_id AND g.recorded_at>=?
                        ORDER BY g.recorded_at DESC LIMIT 1) longitude,
                       (SELECT g.recorded_at FROM gps_position g
                        JOIN trip t ON t.id=g.trip_id AND t.organization_id=s.organization_id
                        JOIN trip_shipment tx ON tx.trip_id=t.id AND tx.shipment_id=s.id
                        WHERE g.organization_id=s.organization_id AND g.recorded_at>=?
                        ORDER BY g.recorded_at DESC LIMIT 1) locationUpdatedAt,
                       (SELECT t.id FROM trip t JOIN trip_shipment tx ON tx.trip_id=t.id
                        WHERE tx.shipment_id=s.id AND t.organization_id=s.organization_id
                        ORDER BY t.created_at DESC LIMIT 1) tripId
                FROM shipment s JOIN shipment_order so ON so.shipment_id=s.id
                JOIN sales_order o ON o.id=so.order_id AND o.organization_id=s.organization_id
                WHERE s.organization_id=? AND o.customer_id=? ORDER BY s.created_at DESC
                """, java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(900)),
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(900)),
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(900)),
                organizationId.toString(), customer(jwt).toString());
        results.forEach(shipment -> {
            Object tripId = shipment.remove("tripId");
            if (tripId != null && shipment.get("latitude") != null) {
                shipment.put("eta", tracking.eta(organizationId.toString(), String.valueOf(tripId))
                        .get("estimatedArrival"));
            } else {
                shipment.put("eta", null);
            }
        });
        return ApiResponse.of(results);
    }

    @GetMapping("/shipments/{shipmentId}/timeline")
    public ApiResponse<List<Map<String, Object>>> shipmentTimeline(@AuthenticationPrincipal Jwt jwt,
                                                                    @PathVariable UUID shipmentId) {
        return ApiResponse.of(db.queryForList("""
                SELECT h.from_status fromStatus,h.to_status toStatus,h.occurred_at occurredAt
                FROM shipment_stop_history h JOIN shipment_stop st ON st.id=h.stop_id
                JOIN shipment s ON s.id=st.shipment_id
                JOIN shipment_order so ON so.shipment_id=s.id
                JOIN sales_order o ON o.id=so.order_id AND o.organization_id=s.organization_id
                WHERE s.id=? AND s.organization_id=? AND o.customer_id=?
                ORDER BY h.occurred_at,h.id
                """, shipmentId.toString(), organization(jwt).toString(), customer(jwt).toString()));
    }

    @GetMapping("/addresses")
    public ApiResponse<List<Map<String, Object>>> addresses(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(customerRecords(jwt, """
                SELECT id,label,address_json address,is_default isDefault,created_at createdAt
                FROM customer_address WHERE organization_id=? AND customer_id=? ORDER BY is_default DESC,created_at DESC
                """));
    }

    @PostMapping("/addresses")
    @Transactional
    public ApiResponse<Map<String, Object>> addAddress(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody AddressRequest request) {
        return saveAddress(jwt, null, request);
    }

    @PutMapping("/addresses/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> updateAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                           @Valid @RequestBody AddressRequest request) {
        return saveAddress(jwt, id, request);
    }

    private ApiResponse<Map<String, Object>> saveAddress(Jwt jwt, UUID addressId, AddressRequest request) {
        if (request.address().size() > 30) throw invalid("Address contains too many fields");
        String encoded;
        try {
            encoded = json.writeValueAsString(request.address());
        } catch (JsonProcessingException exception) {
            throw invalid("Address is not valid JSON");
        }
        if (encoded.length() > 8192) throw invalid("Address must not exceed 8192 characters");
        UUID organizationId = organization(jwt);
        UUID customerId = customer(jwt);
        UUID id = addressId == null ? UUID.randomUUID() : addressId;
        if (addressId != null && db.queryForObject("""
                SELECT COUNT(*) FROM customer_address WHERE id=? AND organization_id=? AND customer_id=?
                """, Integer.class, id.toString(), organizationId.toString(), customerId.toString()) == 0) {
            throw notFound();
        }
        if (request.isDefault()) {
            db.update("UPDATE customer_address SET is_default=FALSE WHERE organization_id=? AND customer_id=? AND id<>?",
                    organizationId.toString(), customerId.toString(), id.toString());
        }
        if (addressId == null) {
            if (db.queryForObject("SELECT COUNT(*) FROM customer_address WHERE organization_id=? AND customer_id=?",
                    Integer.class, organizationId.toString(), customerId.toString()) >= 50) {
                throw invalid("A customer may store at most 50 addresses");
            }
            db.update("""
                    INSERT INTO customer_address(id,organization_id,customer_id,label,address_json,is_default)
                    VALUES(?,?,?,?,?,?)
                    """, id.toString(), organizationId.toString(), customerId.toString(),
                    request.label().trim(), encoded, request.isDefault());
        } else {
            db.update("""
                    UPDATE customer_address SET label=?,address_json=?,is_default=?,updated_at=CURRENT_TIMESTAMP(6)
                    WHERE id=? AND organization_id=? AND customer_id=?
                    """, request.label().trim(), encoded, request.isDefault(), id.toString(),
                    organizationId.toString(), customerId.toString());
        }
        return ApiResponse.of(Map.of("id", id, "label", request.label().trim(),
                "address", request.address(), "isDefault", request.isDefault()));
    }

    @DeleteMapping("/addresses/{id}")
    public ApiResponse<Map<String, Boolean>> deleteAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        int changed = db.update("DELETE FROM customer_address WHERE id=? AND organization_id=? AND customer_id=?",
                id.toString(), organization(jwt).toString(), customer(jwt).toString());
        if (changed == 0) throw notFound();
        return ApiResponse.of(Map.of("deleted", true));
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(@AuthenticationPrincipal Jwt jwt) {
        List<Map<String, Object>> users = db.queryForList("""
                SELECT email_normalized email,phone_normalized phone
                FROM user_account WHERE id=? AND organization_id=? AND customer_id=? AND active=TRUE
                """, jwt.getSubject(), organization(jwt).toString(), customer(jwt).toString());
        if (users.isEmpty()) throw notFound();
        Map<String, Object> profile = new LinkedHashMap<>(users.getFirst());
        profile.put("customerId", customer(jwt));
        List<String> locales = db.queryForList("""
                SELECT preferred_locale FROM customer_portal_profile WHERE organization_id=? AND customer_id=?
                """, String.class, organization(jwt).toString(), customer(jwt).toString());
        profile.put("locale", locales.isEmpty() ? "en" : locales.getFirst());
        return ApiResponse.of(profile);
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileRequest request) {
        db.update("""
                INSERT INTO customer_portal_profile(organization_id,customer_id,preferred_locale)
                VALUES(?,?,?) ON DUPLICATE KEY UPDATE preferred_locale=VALUES(preferred_locale),
                    updated_at=CURRENT_TIMESTAMP(6)
                """, organization(jwt).toString(), customer(jwt).toString(), request.locale());
        return ApiResponse.of(Map.of("customerId", customer(jwt), "locale", request.locale()));
    }

    @GetMapping("/invoices")
    public ApiResponse<List<Map<String, Object>>> invoices(@AuthenticationPrincipal Jwt jwt) {
        return documents(jwt, "INVOICE");
    }

    @GetMapping("/proofs")
    public ApiResponse<List<Map<String, Object>>> proofs(@AuthenticationPrincipal Jwt jwt) {
        return documents(jwt, "POD");
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<StreamingResponseBody> downloadDocument(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT a.object_bucket,a.object_key,a.original_filename,a.detected_content_type,a.scan_status,a.size_bytes
                FROM customer_document d JOIN attachment a ON a.id=d.attachment_id
                WHERE d.id=? AND d.organization_id=? AND d.customer_id=? AND a.organization_id=?
                """, id.toString(), organization(jwt).toString(), customer(jwt).toString(), organization(jwt).toString());
        if (rows.isEmpty() || !"CLEAN".equals(rows.getFirst().get("scan_status"))) throw notFound();
        var document = rows.getFirst();
        try {
            var object = minio.getObject(GetObjectArgs.builder()
                    .bucket((String) document.get("object_bucket"))
                    .object((String) document.get("object_key")).build());
            String filename = String.valueOf(document.get("original_filename")).replaceAll("[\\r\\n\\\\/]", "_");
            String contentType = String.valueOf(document.get("detected_content_type"));
            MediaType mediaType;
            try { mediaType = MediaType.parseMediaType(contentType); }
            catch (RuntimeException exception) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
            StreamingResponseBody body = output -> {
                try (object) { object.transferTo(output); }
            };
            return ResponseEntity.ok().contentType(mediaType)
                    .contentLength(((Number) document.get("size_bytes")).longValue())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename).build().toString())
                    .body(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to stream authorized customer document", exception);
        }
    }

    private ApiResponse<List<Map<String, Object>>> documents(Jwt jwt, String type) {
        return ApiResponse.of(db.queryForList("""
                SELECT d.id,d.document_number documentNumber,d.document_type documentType,
                       d.shipment_id shipmentId,d.created_at createdAt,a.original_filename filename,
                       a.detected_content_type contentType,a.size_bytes sizeBytes
                FROM customer_document d JOIN attachment a ON a.id=d.attachment_id
                WHERE d.organization_id=? AND d.customer_id=? AND d.document_type=? AND a.scan_status='CLEAN'
                ORDER BY d.created_at DESC LIMIT 500
                """, organization(jwt).toString(), customer(jwt).toString(), type));
    }

    private List<Map<String, Object>> customerRecords(Jwt jwt, String query) {
        return db.queryForList(query, organization(jwt).toString(), customer(jwt).toString()).stream().map(row -> {
            Object value = row.get("address");
            try {
                row.put("address", json.readValue(String.valueOf(value), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            } catch (Exception exception) {
                throw new IllegalStateException("Stored customer address is invalid JSON", exception);
            }
            return row;
        }).toList();
    }

    private static UUID organization(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("organizationId"));
    }

    private static UUID customer(Jwt jwt) {
        String customerId = jwt.getClaimAsString("customerId");
        if (customerId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Customer scope is missing");
        }
        return UUID.fromString(customerId);
    }

    private static UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static ResponseStatusException notFound() {
        return new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Customer resource not found");
    }

    private static ResponseStatusException invalid(String message) {
        return new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record CreateOrderRequest(UUID customerId, String fulfillmentType, Map<String, Object> shipTo,
                                     String currency, Instant requestedDeliveryAt,
                                     @NotEmpty List<@Valid LineRequest> lines) {
        OrderService.CreateOrder toOrder() {
            return new OrderService.CreateOrder(customerId, fulfillmentType, shipTo, currency,
                    requestedDeliveryAt, lines.stream().map(LineRequest::toLine).toList());
        }
    }

    public record BulkOrderRequest(@NotEmpty @Size(max = 100) List<@Valid CreateOrderRequest> orders) {}

    public record LineRequest(@NotNull UUID productId, @NotBlank @Size(max = 255) String description,
                              @NotNull @Positive BigDecimal quantity, String unit, BigDecimal unitPrice) {
        OrderService.Line toLine() {
            return new OrderService.Line(productId, description, quantity, unit, unitPrice);
        }
    }

    public record AddressRequest(@NotBlank @Size(max = 100) String label,
                                 @NotEmpty Map<String, Object> address, boolean isDefault) {}

    public record ProfileRequest(@NotBlank @Pattern(regexp = "en|fa") String locale) {}
}
