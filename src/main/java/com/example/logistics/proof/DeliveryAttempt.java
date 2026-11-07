package com.example.logistics.proof;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeliveryAttempt(
        UUID id,
        UUID shipmentId,
        UUID driverId,
        int attemptNumber,
        String status,
        Instant attemptedAt,
        String failureReason,
        List<Photo> photos,
        String recipientName,
        String notes,
        Double latitude,
        Double longitude
) {
    public DeliveryAttempt {
        status = status == null ? "PENDING" : status;
        photos = photos == null ? List.of() : List.copyOf(photos);
        if (id == null) id = UUID.randomUUID();
        if (attemptedAt == null) attemptedAt = Instant.now();
    }

    public boolean failed() {
        return "FAILED".equalsIgnoreCase(status) || failureReason != null && !failureReason.isBlank();
    }
}
