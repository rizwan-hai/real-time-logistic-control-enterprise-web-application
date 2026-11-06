package com.example.logistics.warehouse.api;

import com.example.logistics.shared.api.ApiResponse;
import com.example.logistics.warehouse.application.WarehouseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@RestController @RequestMapping("/api/v1/warehouse")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER','WAREHOUSE_OPERATOR')")
public class WarehouseController {
    private final WarehouseService service;
    public WarehouseController(WarehouseService service){this.service=service;}
    @GetMapping("/dashboard") public ApiResponse<Map<String,Object>> dashboard(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.dashboard(org(jwt)));}
    @GetMapping("/locations") public ApiResponse<List<Map<String,Object>>> locations(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.locations(org(jwt)));}
    @GetMapping("/receipts") public ApiResponse<List<Map<String,Object>>> receipts(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.receipts(org(jwt)));}
    @GetMapping("/putaway") public ApiResponse<List<Map<String,Object>>> putaways(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.putaways(org(jwt)));}
    @GetMapping("/picks") public ApiResponse<List<Map<String,Object>>> picks(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.picks(org(jwt)));}
    @GetMapping("/counts/lines") public ApiResponse<List<Map<String,Object>>> countLines(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.countLines(org(jwt)));}
    @PostMapping("/locations") public ApiResponse<Map<String,Object>> createLocation(@AuthenticationPrincipal Jwt jwt,@RequestBody Map<String,Object> body){return ApiResponse.of(service.createLocation(org(jwt),body));}
    @GetMapping("/inventory") public ApiResponse<List<Map<String,Object>>> inventory(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) String search){return ApiResponse.of(service.inventory(org(jwt),search));}
    @GetMapping("/movements") public ApiResponse<List<Map<String,Object>>> movements(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.movements(org(jwt)));}
    @GetMapping("/putaway/suggestions") public ApiResponse<List<Map<String,Object>>> suggestions(@AuthenticationPrincipal Jwt jwt,@RequestParam UUID productId,@RequestParam BigDecimal quantity){return ApiResponse.of(service.suggest(org(jwt),productId,quantity));}
    @PostMapping("/receipts") public ApiResponse<Map<String,Object>> receive(@AuthenticationPrincipal Jwt jwt,@RequestBody Map<String,Object> body){return ApiResponse.of(service.receive(org(jwt),actor(jwt),body));}
    @PostMapping("/receipts/{receiptId}/lines/{lineId}/quality") public ApiResponse<Map<String,Object>> quality(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID receiptId,@PathVariable UUID lineId,@RequestBody Decision body){return ApiResponse.of(service.quality(org(jwt),actor(jwt),receiptId,lineId,body.decision()));}
    @PostMapping("/putaway/{taskId}/complete") public ApiResponse<Map<String,Object>> putaway(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID taskId,@RequestBody Target body){return ApiResponse.of(service.completePutaway(org(jwt),actor(jwt),taskId,body.locationId()));}
    @PostMapping("/orders/{orderId}/allocate") public ApiResponse<Map<String,Object>> allocate(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID orderId,@RequestParam(defaultValue="FEFO") String strategy){return ApiResponse.of(service.allocate(org(jwt),actor(jwt),orderId,strategy));}
    @PostMapping("/picks/{lineId}/scan") public ApiResponse<Map<String,Object>> pick(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID lineId,@RequestBody Scan body){return ApiResponse.of(service.pick(org(jwt),actor(jwt),lineId,body.quantity(),body.barcode()));}
    @PostMapping("/picks/{lineId}/short") public ApiResponse<Map<String,Object>> shortPick(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID lineId){return ApiResponse.of(service.shortPick(org(jwt),lineId));}
    @PostMapping("/orders/{orderId}/release-reservations") public ApiResponse<Map<String,Object>> release(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID orderId){return ApiResponse.of(service.release(org(jwt),orderId));}
    @PostMapping("/orders/{orderId}/packages") public ApiResponse<Map<String,Object>> pack(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID orderId,@RequestBody PackageRequest body){return ApiResponse.of(service.pack(org(jwt),orderId,body.packageCode(),body.weight()));}
    @PostMapping("/counts") public ApiResponse<Map<String,Object>> createCount(@AuthenticationPrincipal Jwt jwt,@RequestBody CountRequest body){return ApiResponse.of(service.createCount(org(jwt),actor(jwt),body.type(),body.blind()));}
    @PostMapping("/count-lines/{lineId}") public ApiResponse<Map<String,Object>> count(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID lineId,@RequestBody CountValue body){return ApiResponse.of(service.countLine(org(jwt),lineId,body.countedQty()));}
    @PostMapping("/counts/{countId}/approve") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZATION_ADMIN','OPERATIONS_MANAGER')") public ApiResponse<Map<String,Object>> approve(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID countId){return ApiResponse.of(service.approveCount(org(jwt),actor(jwt),countId));}
    @PostMapping("/movements/{movementId}/reverse") public ApiResponse<Map<String,Object>> reverse(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID movementId){return ApiResponse.of(service.reverseMovement(org(jwt),actor(jwt),movementId));}
    private UUID org(Jwt jwt){return UUID.fromString(jwt.getClaimAsString("organizationId"));} private UUID actor(Jwt jwt){try{return UUID.fromString(jwt.getSubject());}catch(Exception e){return null;}}
    public record Decision(String decision){} public record Target(UUID locationId){} public record Scan(BigDecimal quantity,String barcode){} public record PackageRequest(String packageCode,BigDecimal weight){} public record CountRequest(String type,boolean blind){} public record CountValue(BigDecimal countedQty){}
}
