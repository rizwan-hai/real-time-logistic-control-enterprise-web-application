package com.example.logistics.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class Invoice {
    private final UUID id;
    private final String number;
    private final UUID customerId;
    private final String currency;
    private final List<InvoiceLine> lines;
    private final BigDecimal subtotal;
    private final BigDecimal taxAmount;
    private final BigDecimal totalAmount;
    private final Instant issuedAt;
    private final Instant dueAt;
    private InvoiceState state;
    private final List<PaymentAllocation> allocations;

    public Invoice(UUID id, String number, UUID customerId, String currency, List<InvoiceLine> lines,
                  BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount,
                  Instant issuedAt, Instant dueAt, InvoiceState state) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.number = number == null ? "INV-00000001" : number;
        this.customerId = customerId;
        this.currency = currency == null ? "USD" : currency;
        this.lines = List.copyOf(lines == null ? List.of() : lines);
        this.subtotal = subtotal == null ? BigDecimal.ZERO : subtotal;
        this.taxAmount = taxAmount == null ? BigDecimal.ZERO : taxAmount;
        this.totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        this.issuedAt = issuedAt;
        this.dueAt = dueAt;
        this.state = state == null ? InvoiceState.DRAFT : state;
        this.allocations = new ArrayList<>();
    }

    public UUID getId() { return id; }
    public String getNumber() { return number; }
    public UUID getCustomerId() { return customerId; }
    public String getCurrency() { return currency; }
    public List<InvoiceLine> getLines() { return lines; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getDueAt() { return dueAt; }
    public InvoiceState getState() { return state; }
    public List<PaymentAllocation> getAllocations() { return Collections.unmodifiableList(allocations); }

    public void issue() {
        if (state == InvoiceState.DRAFT) {
            state = InvoiceState.ISSUED;
            return;
        }
        throw new IllegalStateException("Only draft invoices may be issued");
    }

    public BigDecimal getPaidAmount() {
        return allocations.stream().map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getRemainingBalance() {
        return totalAmount.subtract(getPaidAmount());
    }

    public void applyAllocation(PaymentAllocation allocation) {
        if (allocation == null) throw new IllegalArgumentException("Allocation is required");
        allocations.add(allocation);
        if (getRemainingBalance().signum() <= 0) {
            state = InvoiceState.PAID;
        } else if (getPaidAmount().signum() > 0) {
            state = InvoiceState.PARTIALLY_PAID;
        }
    }

    public void voidInvoice() {
        if (state == InvoiceState.ISSUED || state == InvoiceState.PARTIALLY_PAID || state == InvoiceState.PAID) {
            state = InvoiceState.VOID;
        }
    }
}

final class InvoiceLine {
    private final UUID id;
    private final String description;
    private final BigDecimal quantity;
    private final BigDecimal unitPrice;
    private final String taxCode;

    public InvoiceLine(UUID id, String description, BigDecimal quantity, BigDecimal unitPrice, String taxCode) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.description = description == null ? "Line item" : description;
        this.quantity = quantity == null ? BigDecimal.ONE : quantity;
        this.unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        this.taxCode = taxCode == null ? "VAT" : taxCode;
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getTaxCode() { return taxCode; }

    public BigDecimal getLineTotal() { return quantity.multiply(unitPrice); }
}

final class CreditNote {
    private final UUID id;
    private final UUID invoiceId;
    private final BigDecimal amount;
    private final String reason;
    private final Instant issuedAt;

    public CreditNote(UUID id, UUID invoiceId, BigDecimal amount, String reason, Instant issuedAt) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.invoiceId = invoiceId;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
        this.reason = reason == null ? "Adjustment" : reason;
        this.issuedAt = issuedAt == null ? Instant.now() : issuedAt;
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public Instant getIssuedAt() { return issuedAt; }
}
