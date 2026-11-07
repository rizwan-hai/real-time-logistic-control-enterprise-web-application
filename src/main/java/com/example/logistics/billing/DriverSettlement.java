package com.example.logistics.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DriverSettlement {
    private final UUID id;
    private final UUID driverId;
    private final String currency;
    private final BigDecimal amount;
    private final Instant settlementDate;
    private final List<SettlementLine> lines;
    private SettlementStatus status;

    public DriverSettlement(UUID id, UUID driverId, String currency, BigDecimal amount,
                           Instant settlementDate, List<SettlementLine> lines, SettlementStatus status) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.driverId = driverId;
        this.currency = currency == null ? "USD" : currency;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
        this.settlementDate = settlementDate == null ? Instant.now() : settlementDate;
        this.lines = lines == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(lines));
        this.status = status == null ? SettlementStatus.DRAFT : status;
    }

    public UUID getId() { return id; }
    public UUID getDriverId() { return driverId; }
    public String getCurrency() { return currency; }
    public BigDecimal getAmount() { return amount; }
    public Instant getSettlementDate() { return settlementDate; }
    public List<SettlementLine> getLines() { return lines; }
    public SettlementStatus getStatus() { return status; }

    public void approve() { status = SettlementStatus.APPROVED; }
    public void markPaid() { status = SettlementStatus.PAID; }
}
