package com.example.logistics.shared.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test")
class ValidationProbeController {
    @PostMapping("/validation") void validate(@Valid @RequestBody ProbeRequest request) {}
    @GetMapping("/invalid") void invalid() { throw new IllegalArgumentException("invalid argument"); }
    record ProbeRequest(@NotBlank String name) {}
}
