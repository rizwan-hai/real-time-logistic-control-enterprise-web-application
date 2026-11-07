package com.example.logistics.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Surcharge {
    private final String name;
    private final BigDecimal amount;
    private final boolean percentage;
    private final String appliedTo;

    public Surcharge(String name, BigDecimal amount, boolean percentage, String appliedTo) {
        this.name = name == null ? "Surcharge" : name;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
        this.percentage = percentage;
        this.appliedTo = appliedTo;
    }

    public String getName() { return name; }
    public BigDecimal getAmount() { return amount; }
    public boolean isPercentage() { return percentage; }
    public String getAppliedTo() { return appliedTo; }

    public BigDecimal calculateAppliedAmount(BigDecimal baseAmount, Tariff.ShipmentQuote shipment) {
        if (amount.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        BigDecimal effective = percentage ? baseAmount.multiply(amount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP) : amount;
        return effective;
    }
}
