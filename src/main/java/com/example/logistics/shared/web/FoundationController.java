package com.example.logistics.shared.web;

import com.example.logistics.shared.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/system")
public class FoundationController {
    @GetMapping("/status")
    public ApiResponse<Map<String, String>> status() {
        return ApiResponse.of(Map.of("status", "UP", "apiVersion", "v1"));
    }
}
