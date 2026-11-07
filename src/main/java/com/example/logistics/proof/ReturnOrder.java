package com.example.logistics.proof;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReturnOrder(
        UUID id,
        UUID shipmentId,
        String reason,
        String disposition,
        String status,
        Instant createdAt,
        List<UUID> stockMovementIds,
        boolean restock,
        boolean scrap,
        String inspectionNotes
) {
    public ReturnOrder {
        if (id == null) id = UUID.randomUUID();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        status = status == null ? "OPEN" : status;
        stockMovementIds = stockMovementIds == null ? List.of() : List.copyOf(stockMovementIds);
    }

    public boolean isRestock() { return restock; }
    public boolean isScrap() { return scrap; }
}
