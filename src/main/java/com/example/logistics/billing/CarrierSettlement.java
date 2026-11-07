package com.example.logistics.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CarrierSettlement {
    private final UUID id;
    private final UUID carrierId;
    private final String currency;
    private final BigDecimal grossAmount;
    private final BigDecimal deductions;
    private final BigDecimal netAmount;
    private final Instant settlementDate;
    private final List<SettlementLine> lines;
    private SettlementStatus status;

    public CarrierSettlement(UUID id, UUID carrierId, String currency, BigDecimal grossAmount,
                            BigDecimal deductions, BigDecimal netAmount, Instant settlementDate,
                            List<SettlementLine> lines, SettlementStatus status) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.carrierId = carrierId;
        this.currency = currency == null ? "USD" : currency;
        this.grossAmount = grossAmount == null ? BigDecimal.ZERO : grossAmount;
        this.deductions = deductions == null ? BigDecimal.ZERO : deductions;
        this.netAmount = netAmount == null ? BigDecimal.ZERO : netAmount;
        this.settlementDate = settlementDate == null ? Instant.now() : settlementDate;
        this.lines = lines == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(lines));
        this.status = status == null ? SettlementStatus.DRAFT : status;
    }

    public UUID getId() { return id; }
    public UUID getCarrierId() { return carrierId; }
    public String getCurrency() { return currency; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getDeductions() { return deductions; }
    public BigDecimal getNetAmount() { return netAmount; }
    public Instant getSettlementDate() { return settlementDate; }
    public List<SettlementLine> getLines() { return lines; }
    public SettlementStatus getStatus() { return status; }

    public void approve() { status = SettlementStatus.APPROVED; }
    public void markPaid() { status = SettlementStatus.PAID; }
}
