package com.example.logistics.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TaxRate {
    private final String name;
    private final BigDecimal rate;

    public TaxRate(String name, BigDecimal rate) {
        this.name = name == null ? "VAT" : name;
        this.rate = rate == null ? BigDecimal.ZERO : rate;
    }

    public String getName() { return name; }
    public BigDecimal getRate() { return rate; }

    public BigDecimal calculateTax(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) == 0 || rate.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return amount.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
