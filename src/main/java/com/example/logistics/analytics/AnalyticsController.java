package com.example.logistics.analytics;

import com.example.logistics.shared.api.ApiException;
import com.example.logistics.shared.api.ApiResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/reports")
public class AnalyticsController {
    private static final String DASHBOARD_ROLES = "hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','ORDER_CLERK','TRANSPORT_DISPATCHER','WAREHOUSE_OPERATOR','FINANCE_CLERK','FINANCE_APPROVER','AUDITOR','CUSTOMER_USER')";
    private static final String EXPORT_ROLES = "hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','TRANSPORT_DISPATCHER','FINANCE_CLERK','FINANCE_APPROVER','AUDITOR','CUSTOMER_USER')";
    private final AnalyticsService analytics;
    private final ReportExportService exports;
    private final ReportExportRunner runner;
    private final JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public AnalyticsController(AnalyticsService analytics, ReportExportService exports,
                               ReportExportRunner runner, JdbcTemplate jdbc, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.analytics = analytics;
        this.exports = exports;
        this.runner = runner;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    private static List<UUID> warehouseScopes(Jwt jwt) {
        Object scopes = jwt.getClaims().get("warehouseIds");
        if (!(scopes instanceof List<?> values)) return List.of();
        return values.stream().map(value -> {
            try {
                return UUID.fromString(String.valueOf(value));
            } catch (IllegalArgumentException exception) {
                throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "WAREHOUSE_SCOPE_INVALID", "Warehouse authorization scope is invalid");
            }
        }).toList();
    }

    @GetMapping("/dashboard")
    @PreAuthorize(DASHBOARD_ROLES)
    public ApiResponse<Map<String, Object>> dashboard(@AuthenticationPrincipal Jwt jwt, @ModelAttribute ReportFilter filter) {
        var filters = filters(filter);
        UUID customerScope = customerScope(jwt);
        var scopedFilters = customerScope == null ? filters : withCustomer(filters, customerScope);
        boolean warehouseOnly = hasRole(jwt, "WAREHOUSE_OPERATOR") && !hasRole(jwt, "OPERATIONS_MANAGER")
                && !hasRole(jwt, "ORGANIZATION_ADMIN") && !hasRole(jwt, "PLATFORM_ADMIN");
        List<UUID> warehouseScopes = warehouseOnly ? warehouseScopes(jwt) : List.of();
        if (hasRole(jwt, "WAREHOUSE_OPERATOR")) {
            if (warehouseScopes.isEmpty() || scopedFilters.warehouseId() != null
                    && !warehouseScopes.contains(scopedFilters.warehouseId())) {
                throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "WAREHOUSE_SCOPE_FORBIDDEN", "No access to the requested warehouse");
            }
        }
        return ApiResponse.of(analytics.dashboard(organization(jwt), customerScope, scopedFilters,
                categories(jwt), warehouseScopes));
    }

    @GetMapping("/deliveries")
    @PreAuthorize(DASHBOARD_ROLES)
    public ApiResponse<List<Map<String, Object>>> deliveries(@AuthenticationPrincipal Jwt jwt,
                                                              @ModelAttribute ReportFilter filter,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "100") int size) {
        if (!categories(jwt).contains("delivery")) throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                "REPORT_SCOPE_FORBIDDEN", "Delivery detail is not available for this role");
        if (page < 0 || size < 1 || size > 500) throw bad("Page must be non-negative and size must be between 1 and 500");
        var filters = filters(filter);
        UUID customerScope = customerScope(jwt);
        return ApiResponse.of(analytics.shipmentRows(organization(jwt),
                customerScope == null ? filters.customerId() : customerScope, filters, page, size));
    }

    @GetMapping("/trends")
    @PreAuthorize(DASHBOARD_ROLES)
    public ApiResponse<List<Map<String, Object>>> trends(@AuthenticationPrincipal Jwt jwt,
                                                          @ModelAttribute ReportFilter filter) {
        var filters = filters(filter);
        UUID customerScope = customerScope(jwt);
        if (!categories(jwt).contains("delivery")) return ApiResponse.of(List.of());
        return ApiResponse.of(analytics.deliveryTrends(organization(jwt), customerScope,
                customerScope == null ? filters : withCustomer(filters, customerScope)));
    }

    @PostMapping("/exports")
    @PreAuthorize(EXPORT_ROLES)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createExport(@AuthenticationPrincipal Jwt jwt,
                                                                          @ModelAttribute ReportFilter filter,
                                                                          @RequestParam(defaultValue = "CSV") String format) {
        String normalized = format.toUpperCase(java.util.Locale.ROOT);
        if (!List.of("CSV", "XLSX", "PDF").contains(normalized)) throw bad("Format must be CSV, XLSX, or PDF");
        UUID customerScope = customerScope(jwt);
        var reportFilters = filters(filter);
        if (customerScope != null) reportFilters = withCustomer(reportFilters, customerScope);
        UUID id = exports.create(organization(jwt), actor(jwt), reportFilters, normalized);
        runner.generate(id, organization(jwt), actor(jwt), reportFilters, normalized);
        return ResponseEntity.accepted().body(ApiResponse.of(Map.of("id", id, "status", "QUEUED")));
    }

    @GetMapping("/exports")
    @PreAuthorize(EXPORT_ROLES)
    public ApiResponse<List<Map<String, Object>>> exportJobs(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(exports.jobs(organization(jwt), actor(jwt)));
    }

    @GetMapping("/exports/{id}/download")
    @PreAuthorize(EXPORT_ROLES)
    public ResponseEntity<FileSystemResource> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        List<String> authorities = jwt.getClaimAsStringList("authorities");
        boolean downloadAny = authorities != null && authorities.contains("report.exports.download");
        var file = exports.downloadable(organization(jwt), actor(jwt), id, downloadAny);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(mediaType(file.getFileName().toString()))
                .body(new FileSystemResource(file));
    }

    @GetMapping("/schedules")
    @PreAuthorize(EXPORT_ROLES)
    public ApiResponse<List<Map<String, Object>>> schedules(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(jdbc.queryForList("""
                SELECT id,report_type reportType,export_format exportFormat,frequency,filters_json filters,
                       enabled,next_run_at nextRunAt,created_at createdAt
                FROM report_schedule WHERE organization_id=? AND requested_by=? ORDER BY created_at DESC
                """, organization(jwt).toString(), actor(jwt).toString()));
    }

    @PostMapping("/schedules")
    @PreAuthorize(EXPORT_ROLES)
    public ApiResponse<Map<String, Object>> schedule(@AuthenticationPrincipal Jwt jwt,
                                                      @ModelAttribute ReportFilter filter,
                                                      @RequestParam(defaultValue = "CSV") String format,
                                                      @RequestParam String frequency) {
        String normalizedFormat = format.toUpperCase(java.util.Locale.ROOT);
        String normalizedFrequency = frequency.toUpperCase(java.util.Locale.ROOT);
        if (!List.of("CSV", "XLSX", "PDF").contains(normalizedFormat)
                || !List.of("DAILY", "WEEKLY", "MONTHLY").contains(normalizedFrequency)) {
            throw bad("Choose CSV, XLSX, or PDF and a DAILY, WEEKLY, or MONTHLY schedule");
        }
        UUID customerScope = customerScope(jwt);
        var reportFilters = filters(filter);
        if (customerScope != null) reportFilters = withCustomer(reportFilters, customerScope);
        UUID id = UUID.randomUUID();
        LocalDateTimeHolder next = nextRun(normalizedFrequency);
        jdbc.update("""
                INSERT INTO report_schedule(id,organization_id,requested_by,report_type,export_format,frequency,filters_json,next_run_at)
                VALUES(?,?,?,'DELIVERY',?,?,?,?)
                """, id.toString(), organization(jwt).toString(), actor(jwt).toString(), normalizedFormat,
                normalizedFrequency, writeFilters(reportFilters), Timestamp.valueOf(next.value()));
        return ApiResponse.of(Map.of("id", id, "frequency", normalizedFrequency, "nextRunAt", next.value()));
    }

    @DeleteMapping("/schedules/{id}")
    @PreAuthorize(EXPORT_ROLES)
    public ApiResponse<Map<String, Boolean>> deleteSchedule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        int deleted = jdbc.update("DELETE FROM report_schedule WHERE id=? AND organization_id=? AND requested_by=?",
                id.toString(), organization(jwt).toString(), actor(jwt).toString());
        if (deleted == 0) throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                "REPORT_SCHEDULE_NOT_FOUND", "Report schedule not found");
        return ApiResponse.of(Map.of("deleted", true));
    }

    private AnalyticsService.Filters filters(ReportFilter filter) {
        LocalDate to = filter.to() == null ? LocalDate.now() : filter.to();
        LocalDate from = filter.from() == null ? to.minusDays(29) : filter.from();
        if (from.isAfter(to) || from.isBefore(to.minusYears(1))) throw bad("Date range must be ordered and no longer than one year");
        return new AnalyticsService.Filters(from, to, filter.customerId(), filter.warehouseId(), filter.vehicleId(), filter.driverId());
    }

    private static AnalyticsService.Filters withCustomer(AnalyticsService.Filters source, UUID customer) {
        return new AnalyticsService.Filters(source.from(), source.to(), customer, source.warehouseId(), source.vehicleId(), source.driverId());
    }

    private static UUID customerScope(Jwt jwt) {
        if (jwt.getClaimAsString("customerId") == null) return null;
        return hasRole(jwt, "CUSTOMER_USER") ? UUID.fromString(jwt.getClaimAsString("customerId")) : null;
    }

    private static List<String> categories(Jwt jwt) {
        if (hasRole(jwt, "CUSTOMER_USER")) return List.of("delivery", "financial");
        if (hasRole(jwt, "PLATFORM_ADMIN") || hasRole(jwt, "ORGANIZATION_ADMIN") || hasRole(jwt, "OPERATIONS_MANAGER")) {
            return List.of("delivery", "fleet", "warehouse", "financial");
        }
        if (hasRole(jwt, "WAREHOUSE_OPERATOR")) return List.of("warehouse");
        if (hasRole(jwt, "FINANCE_CLERK") || hasRole(jwt, "FINANCE_APPROVER")) return List.of("financial");
        if (hasRole(jwt, "TRANSPORT_DISPATCHER")) return List.of("delivery", "fleet");
        if (hasRole(jwt, "ORDER_CLERK")) return List.of("delivery");
        return List.of("delivery", "fleet", "warehouse", "financial");
    }

    private static boolean hasRole(Jwt jwt, String role) {
        List<String> authorities = jwt.getClaimAsStringList("authorities");
        return authorities != null && authorities.contains("ROLE_" + role);
    }

    private static UUID organization(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("organizationId")); }
    private static UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private static ApiException bad(String message) {
        return new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "REPORT_FILTER_INVALID", message);
    }
    private static org.springframework.http.MediaType mediaType(String file) {
        if (file.endsWith(".xlsx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        if (file.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        return MediaType.parseMediaType("text/csv");
    }
    private String writeFilters(AnalyticsService.Filters filters) {
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize report schedule filters", exception);
        }
    }
    private static LocalDateTimeHolder nextRun(String frequency) {
        var value = java.time.LocalDateTime.now().withSecond(0).withNano(0);
        value = switch (frequency) {
            case "DAILY" -> value.plusDays(1);
            case "WEEKLY" -> value.plusWeeks(1);
            case "MONTHLY" -> value.plusMonths(1);
            default -> throw bad("Unsupported schedule frequency");
        };
        return new LocalDateTimeHolder(value);
    }

    public record ReportFilter(LocalDate from, LocalDate to, UUID customerId, UUID warehouseId, UUID vehicleId, UUID driverId) {}
    private record LocalDateTimeHolder(java.time.LocalDateTime value) {}
}
