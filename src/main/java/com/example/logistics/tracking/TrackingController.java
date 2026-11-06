package com.example.logistics.tracking;

import com.example.logistics.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController @RequestMapping("/api/v1/tracking")
public class TrackingController {
    private final TrackingService service;
    public TrackingController(TrackingService service){this.service=service;}

    @PostMapping("/positions") @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Map<String,Object>> positions(@RequestHeader("X-Device-Id") @NotBlank @Size(max=36) String deviceId,@RequestHeader("X-Device-Token") @NotBlank @Size(min=64,max=64) String token,@Valid @RequestBody Batch batch){
        if(batch.positions().size()>500)throw new IllegalArgumentException("Maximum batch size is 500");
        var device=service.authenticate(deviceId,token); Instant now=Instant.now();
        for(var p:batch.positions()){GpsRules.validate(p.recordedAt(),p.latitude(),p.longitude(),p.speedKph(),p.heading(),now);if(p.accuracyM()!=null&&p.accuracyM()>10000)throw new IllegalArgumentException("Accuracy must not exceed 10000 meters");if(p.tripId()!=null)service.validateTrip(device,p.tripId());}
        var positions=batch.positions().stream().map(PositionInput::toPosition).toList();service.checkIdempotency(device,positions);service.ingest(device,positions);
        return ApiResponse.of(Map.of("accepted",batch.positions().size(),"batchId",UUID.randomUUID().toString(),"processing","QUEUED"));
    }
    @PostMapping("/devices") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER')")
    public ApiResponse<Map<String,Object>> provisionDevice(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody DeviceRequest body){return ApiResponse.of(service.provisionDevice(jwt.getClaimAsString("organizationId"),body.vehicleId().toString(),body.driverId()==null?null:body.driverId().toString()));}

    @GetMapping("/vehicles/latest") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','TRANSPORT_DISPATCHER')")
    public ApiResponse<List<Map<String,Object>>> latest(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.latest(jwt.getClaimAsString("organizationId")));}
    @GetMapping("/trips/{tripId}/eta") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','TRANSPORT_DISPATCHER')")
    public ApiResponse<Map<String,Object>> eta(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID tripId){return ApiResponse.of(service.eta(jwt.getClaimAsString("organizationId"),tripId.toString()));}
    @GetMapping("/trips/{tripId}/history") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','TRANSPORT_DISPATCHER')")
    public ApiResponse<List<Map<String,Object>>> history(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID tripId,@RequestParam Instant from,@RequestParam Instant to){return ApiResponse.of(service.tripHistory(jwt.getClaimAsString("organizationId"),tripId.toString(),from,to));}

    @GetMapping("/geofences") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','TRANSPORT_DISPATCHER')")
    public ApiResponse<List<Map<String,Object>>> geofences(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.geofences(jwt.getClaimAsString("organizationId")));}
    @PostMapping("/geofences") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER')")
    public ApiResponse<Map<String,Object>> createGeofence(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Geofence body){return ApiResponse.of(service.createGeofence(jwt.getClaimAsString("organizationId"),body.name(),body.kind(),body.shapeJson(),body.debounceSeconds()));}
    @GetMapping("/alerts") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','TRANSPORT_DISPATCHER')")
    public ApiResponse<List<Map<String,Object>>> alerts(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.alerts(jwt.getClaimAsString("organizationId")));}
    @GetMapping("/alerts/{id}/history") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','TRANSPORT_DISPATCHER')")
    public ApiResponse<List<Map<String,Object>>> alertHistory(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return ApiResponse.of(service.alertHistory(jwt.getClaimAsString("organizationId"),id.toString()));}
    @PostMapping("/alerts/{id}/actions") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','TRANSPORT_DISPATCHER')")
    public ApiResponse<Map<String,Object>> alertAction(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody AlertAction body){return ApiResponse.of(service.alertAction(jwt.getClaimAsString("organizationId"),id.toString(),jwt.getSubject(),body.action(),body.note()));}
    @PutMapping("/alerts/{id}/assignment") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','TRANSPORT_DISPATCHER')")
    public ApiResponse<Map<String,Object>> assignAlert(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody AlertAssignment body){return ApiResponse.of(service.assignAlert(jwt.getClaimAsString("organizationId"),id.toString(),jwt.getSubject(),body.assignedTo()==null?null:body.assignedTo().toString()));}
    @PostMapping("/shipments/{shipmentId}/public-link") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','TRANSPORT_DISPATCHER')")
    public ApiResponse<Map<String,Object>> publicLink(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID shipmentId,@RequestParam(defaultValue="7") @Min(1) @Max(30) int days){return ApiResponse.of(service.issuePublicToken(jwt.getClaimAsString("organizationId"),shipmentId.toString(),days));}
    @DeleteMapping("/shipments/{shipmentId}/public-link/{linkId}") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','TRANSPORT_DISPATCHER')")
    public ApiResponse<Map<String,Object>> revokePublicLink(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID shipmentId,@PathVariable UUID linkId){return ApiResponse.of(service.revokePublicToken(jwt.getClaimAsString("organizationId"),shipmentId.toString(),linkId.toString()));}
    @GetMapping("/public/{token}") public ApiResponse<Map<String,Object>> publicTracking(@PathVariable @Size(min=64,max=64) String token){return ApiResponse.of(service.publicShipment(token));}

    @ExceptionHandler(TrackingService.RateLimitException.class) @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<Map<String,String>> limited(){return ApiResponse.of(Map.of("code","RATE_LIMITED"));}
    @ExceptionHandler(TrackingService.DeviceAuthenticationException.class) @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Map<String,String>> deviceAuthenticationFailed(){return ApiResponse.of(Map.of("code","DEVICE_CREDENTIALS_INVALID"));}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiResponse<Map<String,String>> invalid(IllegalArgumentException ex){return ApiResponse.of(Map.of("code","GPS_INVALID","message",ex.getMessage()));}

    public record Batch(@NotEmpty List<@Valid PositionInput> positions){}
    public record DeviceRequest(@NotNull UUID vehicleId,UUID driverId){}
    public record Geofence(@NotBlank String name,@NotBlank String kind,@NotBlank String shapeJson,@Min(0) @Max(3600) int debounceSeconds){}
    public record AlertAction(@NotBlank String action,@Size(max=500) String note){}
    public record AlertAssignment(UUID assignedTo){}
    public record PositionInput(@NotBlank @Size(max=100) String eventId,@NotNull Instant recordedAt,@DecimalMin("-90") @DecimalMax("90") double latitude,@DecimalMin("-180") @DecimalMax("180") double longitude,@DecimalMin("0") @DecimalMax("220") Double speedKph,@DecimalMin("0") @DecimalMax("359.999") Double heading,@DecimalMin("0") @DecimalMax("10000") Double accuracyM,@Min(0) @Max(100) Integer batteryPercent,@NotBlank @Size(max=24) String source,@Pattern(regexp="(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String tripId){
        TrackingService.Position toPosition(){return new TrackingService.Position(eventId,recordedAt,latitude,longitude,speedKph,heading,accuracyM,batteryPercent,source,tripId);}
    }
}
