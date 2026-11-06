package com.example.logistics.orders.api;

import com.example.logistics.orders.application.OrderService;
import com.example.logistics.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/orders")
public class OrderController {
 private final OrderService service; public OrderController(OrderService service){this.service=service;}
 @GetMapping @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','ORDER_CLERK','TRANSPORT_DISPATCHER')")
 public ApiResponse<List<Map<String,Object>>> list(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.list(org(jwt)));}
 @GetMapping("/{id}") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','ORDER_CLERK','TRANSPORT_DISPATCHER')")
 public ApiResponse<Map<String,Object>> get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return ApiResponse.of(service.get(org(jwt),id));}
 @PostMapping @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','ORDER_CLERK')")
 public ApiResponse<Map<String,Object>> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody OrderService.CreateOrder body){return ApiResponse.of(service.create(org(jwt),actor(jwt),body));}
 @PostMapping("/{id}/commands/{command}") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','ORDER_CLERK','TRANSPORT_DISPATCHER','WAREHOUSE_OPERATOR')")
 public ApiResponse<Map<String,Object>> command(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@PathVariable String command,@RequestBody(required=false) Command body){return ApiResponse.of(service.transition(org(jwt),actor(jwt),id,command.toUpperCase(Locale.ROOT),body==null?"":body.reason()));}
 @PostMapping("/{id}/comments") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','ORDER_CLERK')")
 public ApiResponse<Map<String,Boolean>> comment(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Comment body){service.comment(org(jwt),actor(jwt),id,body.body());return ApiResponse.of(Map.of("created",true));}
 private UUID org(Jwt j){return UUID.fromString(j.getClaimAsString("organizationId"));} private UUID actor(Jwt j){try{return UUID.fromString(j.getSubject());}catch(Exception e){return null;}}
 public record Command(String reason){} public record Comment(@NotBlank String body){}
}
