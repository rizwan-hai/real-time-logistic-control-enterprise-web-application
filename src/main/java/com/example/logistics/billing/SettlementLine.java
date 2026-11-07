package com.example.logistics.billing;

import java.math.BigDecimal;
import java.util.UUID;

public class SettlementLine {
    private final UUID id;
    private final String description;
    private final BigDecimal amount;
    private final String category;

    public SettlementLine(UUID id, String description, BigDecimal amount, String category) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.description = description == null ? "Settlement line" : description;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
        this.category = category == null ? "GENERAL" : category;
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public String getCategory() { return category; }
}
