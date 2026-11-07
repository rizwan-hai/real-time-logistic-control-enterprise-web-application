package com.example.logistics.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class Payment {
    private final UUID id;
    private final UUID customerId;
    private final String method;
    private final BigDecimal amount;
    private final String currency;
    private final String externalReference;
    private final Instant createdAt;
    private final List<PaymentAllocation> allocations;
    private PaymentStatus status;

    public Payment(UUID id, UUID customerId, String method, BigDecimal amount, String currency,
                  String externalReference, Instant createdAt) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.customerId = customerId;
        this.method = method == null ? "CASH" : method;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
        this.currency = currency == null ? "USD" : currency;
        this.externalReference = externalReference == null ? "" : externalReference;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.allocations = new ArrayList<>();
        this.status = PaymentStatus.POSTED;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getMethod() { return method; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getExternalReference() { return externalReference; }
    public Instant getCreatedAt() { return createdAt; }
    public List<PaymentAllocation> getAllocations() { return Collections.unmodifiableList(allocations); }
    public PaymentStatus getStatus() { return status; }

    public void allocate(PaymentAllocation allocation) {
        allocations.add(allocation);
        BigDecimal allocated = allocations.stream().map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(amount) > 0) {
            status = PaymentStatus.OVERPAID;
        } else if (allocated.compareTo(amount) == 0) {
            status = PaymentStatus.ALLOCATED;
        } else {
            status = PaymentStatus.PARTIALLY_ALLOCATED;
        }
    }
}

final class PaymentAllocation {
    private final UUID paymentId;
    private final UUID invoiceId;
    private final BigDecimal amount;

    public PaymentAllocation(UUID paymentId, UUID invoiceId, BigDecimal amount) {
        this.paymentId = paymentId;
        this.invoiceId = invoiceId;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
    }

    public UUID getPaymentId() { return paymentId; }
    public UUID getInvoiceId() { return invoiceId; }
    public BigDecimal getAmount() { return amount; }
}
