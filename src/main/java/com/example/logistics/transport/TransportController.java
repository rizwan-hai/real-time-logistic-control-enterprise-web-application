package com.example.logistics.transport;

import com.example.logistics.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api/v1/transport")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','TRANSPORT_DISPATCHER')")
public class TransportController {
 private final TransportService service; private final OperationalValidator validator; public TransportController(TransportService service,OperationalValidator validator){this.service=service;this.validator=validator;}
 @GetMapping("/trips") public ApiResponse<List<Map<String,Object>>> trips(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.trips(org(jwt)));}
 @GetMapping("/trips/{id}") public ApiResponse<Map<String,Object>> trip(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return ApiResponse.of(service.trip(org(jwt),id));}
 @PostMapping("/trips") public ApiResponse<Map<String,Object>> create(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.create(org(jwt),actor(jwt)));}
 @PostMapping("/trips/{id}/dispatch") public ApiResponse<Map<String,Object>> dispatch(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Dispatch body){return ApiResponse.of(service.dispatch(org(jwt),actor(jwt),id,body.driverId(),body.vehicleId(),body.shipmentIds()));}
 @PostMapping("/trips/{id}/commands/{command}") public ApiResponse<Map<String,Object>> command(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@PathVariable String command,@RequestBody(required=false) Odometer body){return ApiResponse.of(service.command(org(jwt),actor(jwt),id,command,body==null?null:body.odometer()));}
 @PostMapping("/trips/{id}/expenses") public ApiResponse<Map<String,Object>> expense(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Expense b){return ApiResponse.of(service.expense(org(jwt),id,b.category(),b.amount(),b.currency(),b.description(),b.incurredAt()));}
 @PostMapping("/trips/{id}/fuel-logs") public ApiResponse<Map<String,Object>> fuel(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Fuel b){return ApiResponse.of(service.fuel(org(jwt),id,b.quantity(),b.unit(),b.unitPrice(),b.currency(),b.odometer(),b.fueledAt(),b.vendor()));}
 @PostMapping("/route-preview") public ApiResponse<Map<String,Object>> preview(@Valid @RequestBody RouteRequest b){return ApiResponse.of(service.preview(b.stops(),b.origin()));}
 @PostMapping("/validate") public ApiResponse<List<String>> validate(@Valid @RequestBody ValidationRequest b){return ApiResponse.of(validator.validate(b.load(),b.vehicle(),b.driver(),b.plannedDrive(),b.plannedWork()));}
 @GetMapping("/tenders") public ApiResponse<List<Map<String,Object>>> tenders(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.tenders(org(jwt)));}
 @PostMapping("/tenders") public ApiResponse<Map<String,Object>> tender(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Tender b){return ApiResponse.of(service.tender(org(jwt),b.shipmentId(),b.carrierId()));}
 @PostMapping("/tenders/{id}/quote") public ApiResponse<Map<String,Object>> quote(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Quote b){return ApiResponse.of(service.quote(org(jwt),id,b.cost(),b.currency(),b.note()));}
 @PostMapping("/tenders/{id}/award") public ApiResponse<Map<String,Object>> award(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return ApiResponse.of(service.award(org(jwt),id));}
 private UUID org(Jwt j){return UUID.fromString(j.getClaimAsString("organizationId"));}private UUID actor(Jwt j){try{return UUID.fromString(j.getSubject());}catch(Exception e){return null;}}
 public record Dispatch(@NotNull UUID driverId,@NotNull UUID vehicleId,List<UUID> shipmentIds){} public record Odometer(@NotNull @DecimalMin("0") BigDecimal odometer){}
 public record Expense(@NotBlank String category,@NotNull @DecimalMin("0") BigDecimal amount,@Pattern(regexp="[A-Z]{3}") String currency,@NotBlank String description,@NotNull Instant incurredAt){}
 public record Fuel(@NotNull @DecimalMin(value="0.001") BigDecimal quantity,@NotBlank String unit,@NotNull @DecimalMin("0") BigDecimal unitPrice,@Pattern(regexp="[A-Z]{3}") String currency,@NotNull @DecimalMin("0") BigDecimal odometer,@NotNull Instant fueledAt,@NotBlank String vendor){}
 public record Tender(@NotNull UUID shipmentId,@NotNull UUID carrierId){} public record Quote(@NotNull @DecimalMin("0") BigDecimal cost,@Pattern(regexp="[A-Z]{3}") String currency,String note){}
 public record RouteRequest(@NotNull RouteOptimizer.Point origin,@NotNull List<RouteOptimizer.Stop> stops){}
 public record ValidationRequest(@NotNull OperationalValidator.Load load,@NotNull OperationalValidator.Vehicle vehicle,@NotNull OperationalValidator.Driver driver,@NotNull java.time.Duration plannedDrive,@NotNull java.time.Duration plannedWork){}
}
