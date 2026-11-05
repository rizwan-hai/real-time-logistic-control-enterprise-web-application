package com.example.logistics.shared.api;

import java.time.Instant;

public record ApiResponse<T>(T data, Meta meta) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, new Meta(Instant.now()));
    }
    public record Meta(Instant timestamp) {}
}
