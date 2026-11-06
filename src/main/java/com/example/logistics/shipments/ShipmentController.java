package com.example.logistics.shipments;
import com.example.logistics.shared.api.ApiResponse;
import jakarta.validation.Valid;import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.security.oauth2.jwt.Jwt;import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/shipments") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','TRANSPORT_DISPATCHER')")
public class ShipmentController{
 private final ShipmentService service;public ShipmentController(ShipmentService service){this.service=service;}
 @GetMapping public ApiResponse<List<Map<String,Object>>> list(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.list(org(jwt)));}
 @GetMapping("/{id}") public ApiResponse<Map<String,Object>> get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return ApiResponse.of(service.get(org(jwt),id));}
 @PostMapping public ApiResponse<Map<String,Object>> build(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Build body){return ApiResponse.of(service.build(org(jwt),actor(jwt),body.orderIds()));}
 @PostMapping("/stops/{stopId}/commands/{command}") public ApiResponse<Map<String,Object>> stop(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID stopId,@PathVariable String command,@RequestBody(required=false) Reason body){return ApiResponse.of(service.stop(org(jwt),actor(jwt),stopId,command,body==null?"":body.reason()));}
 private UUID org(Jwt j){return UUID.fromString(j.getClaimAsString("organizationId"));}private UUID actor(Jwt j){try{return UUID.fromString(j.getSubject());}catch(Exception e){return null;}}
 public record Build(@NotEmpty List<UUID> orderIds){}public record Reason(String reason){}
}
