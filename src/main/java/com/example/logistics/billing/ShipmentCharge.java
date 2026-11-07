package com.example.logistics.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ShipmentCharge {
    private final UUID id;
    private final UUID shipmentId;
    private final UUID customerId;
    private final String currency;
    private final BigDecimal baseAmount;
    private final BigDecimal surchargeAmount;
    private final BigDecimal taxAmount;
    private final BigDecimal totalAmount;
    private final Map<String, BigDecimal> components;
    private final Instant createdAt;

    public ShipmentCharge(UUID id, UUID shipmentId, UUID customerId, String currency, BigDecimal baseAmount,
                         BigDecimal surchargeAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                         Map<String, BigDecimal> components, Instant createdAt) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.shipmentId = shipmentId;
        this.customerId = customerId;
        this.currency = currency == null ? "USD" : currency;
        this.baseAmount = baseAmount == null ? BigDecimal.ZERO : baseAmount;
        this.surchargeAmount = surchargeAmount == null ? BigDecimal.ZERO : surchargeAmount;
        this.taxAmount = taxAmount == null ? BigDecimal.ZERO : taxAmount;
        this.totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        this.components = Map.copyOf(components == null ? Map.of() : components);
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public UUID getId() { return id; }
    public UUID getShipmentId() { return shipmentId; }
    public UUID getCustomerId() { return customerId; }
    public String getCurrency() { return currency; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public BigDecimal getSurchargeAmount() { return surchargeAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Map<String, BigDecimal> getComponents() { return components; }
    public Instant getCreatedAt() { return createdAt; }
}
