package com.example.logistics.catalog.api;

import com.example.logistics.catalog.domain.MasterDataRecordEntity;
import com.example.logistics.catalog.infrastructure.MasterDataRecordRepository;
import com.example.logistics.shared.api.ApiException;
import com.example.logistics.shared.api.ApiPage;
import com.example.logistics.shared.api.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/master-data/{resourceType}")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER')")
public class MasterDataController {
    private static final Set<String> TYPES = Set.of("customers", "suppliers", "carriers", "products", "vehicles", "drivers", "warehouses", "warehouse-zones", "aisles", "racks", "levels", "bins", "contacts", "addresses", "documents", "contracts", "tariffs", "rate-cards", "service-regions", "regions", "delivery-zones", "tariff-zones", "business-calendars", "holidays");
    private final MasterDataRecordRepository records;
    private final ObjectMapper mapper;
    public MasterDataController(MasterDataRecordRepository records, ObjectMapper mapper) { this.records = records; this.mapper = mapper; }

    @GetMapping @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','ORDER_CLERK','TRANSPORT_DISPATCHER','WAREHOUSE_OPERATOR')")
    public ApiResponse<ApiPage<RecordView>> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String resourceType,
            @RequestParam(defaultValue="") String search, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size, @RequestParam(required=false) String status) {
        type(resourceType); paging(page, size);
        Specification<MasterDataRecordEntity> criteria = (root, query, cb) -> cb.and(
                cb.equal(root.get("organizationId"), org(jwt)), cb.equal(root.get("resourceType"), resourceType),
                cb.or(cb.like(cb.lower(root.get("name")), "%"+search.toLowerCase()+"%"), cb.like(cb.lower(root.get("code")), "%"+search.toLowerCase()+"%")));
        if (status != null && !status.isBlank()) criteria = criteria.and((root, query, cb) -> cb.equal(root.get("status"), status.trim().toUpperCase()));
        var filtered = records.findAll(criteria, PageRequest.of(page, size, Sort.by("name").ascending()));
        return ApiResponse.of(ApiPage.from(filtered.map(this::view)));
    }
    @GetMapping("/{id}") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','ORDER_CLERK','TRANSPORT_DISPATCHER','WAREHOUSE_OPERATOR')")
    public ApiResponse<RecordView> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String resourceType, @PathVariable UUID id) { return ApiResponse.of(view(find(jwt, resourceType, id))); }
    @PostMapping @Transactional
    public ApiResponse<RecordView> create(@AuthenticationPrincipal Jwt jwt, @PathVariable String resourceType, @Valid @RequestBody RecordRequest body) {
        type(resourceType); String code = body.code().trim();
        if (records.existsByOrganizationIdAndResourceTypeAndCodeIgnoreCase(org(jwt), resourceType, code)) throw bad("Code already exists");
        var record = new MasterDataRecordEntity(); record.setOrganizationId(org(jwt)); record.setResourceType(resourceType); apply(record, body); return ApiResponse.of(view(records.save(record)));
    }
    @PutMapping("/{id}") @Transactional
    public ApiResponse<RecordView> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String resourceType, @PathVariable UUID id, @Valid @RequestBody RecordRequest body) {
        var record = find(jwt, resourceType, id); String code = body.code().trim();
        if (!record.getCode().equalsIgnoreCase(code) && records.existsByOrganizationIdAndResourceTypeAndCodeIgnoreCase(org(jwt), resourceType, code)) throw bad("Code already exists");
        apply(record, body); return ApiResponse.of(view(records.save(record)));
    }
    @DeleteMapping("/{id}") @Transactional
    public ApiResponse<Map<String, Boolean>> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String resourceType, @PathVariable UUID id) { records.delete(find(jwt, resourceType, id)); return ApiResponse.of(Map.of("deleted", true)); }

    private void apply(MasterDataRecordEntity entity, RecordRequest body) {
        entity.setCode(body.code().trim()); entity.setName(body.name().trim()); entity.setStatus(body.status() == null || body.status().isBlank() ? "ACTIVE" : body.status().trim().toUpperCase());
        try { entity.setAttributesJson(mapper.writeValueAsString(body.attributes() == null ? Map.of() : body.attributes())); }
        catch (Exception ex) { throw bad("Attributes must be valid JSON values"); }
    }
    private RecordView view(MasterDataRecordEntity entity) {
        try { return new RecordView(entity.getId(), entity.getResourceType(), entity.getCode(), entity.getName(), entity.getStatus(), mapper.readValue(entity.getAttributesJson(), new TypeReference<>() {}), entity.getCreatedAt(), entity.getUpdatedAt()); }
        catch (Exception ex) { throw new IllegalStateException("Stored master data is invalid", ex); }
    }
    private MasterDataRecordEntity find(Jwt jwt, String type, UUID id) { type(type); return records.findByIdAndOrganizationIdAndResourceType(id, org(jwt), type).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MASTER_DATA_NOT_FOUND", "Record not found")); }
    private void type(String value) { if (!TYPES.contains(value)) throw new ApiException(HttpStatus.NOT_FOUND, "MASTER_DATA_TYPE_NOT_FOUND", "Unknown master data type"); }
    private void paging(int page, int size) { if (page < 0 || size < 1 || size > 100) throw bad("Page must be non-negative and size must be between 1 and 100"); }
    private UUID org(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("organizationId")); }
    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "MASTER_DATA_INVALID", message); }
    public record RecordRequest(@NotBlank @Size(max=100) String code, @NotBlank @Size(max=200) String name, @Size(max=40) String status, Map<String,Object> attributes) {}
    public record RecordView(UUID id, String resourceType, String code, String name, String status, Map<String,Object> attributes, java.time.Instant createdAt, java.time.Instant updatedAt) {}
}
