package com.example.logistics.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingDomainTest {
    @Test
    void tariffCalculationsSupportMinimumChargeAndTaxes() {
        UUID customerId = UUID.randomUUID();
        Tariff tariff = new Tariff(
            UUID.randomUUID(),
            "Core express",
            "USD",
            List.of(
                new TariffRule(
                    UUID.randomUUID(),
                    "Urban rule",
                    PricingModel.PER_KILOMETER,
                    new BigDecimal("1.50"),
                    new BigDecimal("100.00"),
                    BigDecimal.ONE,
                    Map.of(),
                    customerId,
                    "NORTH",
                    "CENTRAL",
                    "VAN",
                    "EXPRESS",
                    "A",
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2027-01-01T00:00:00Z"),
                    10
                )
            ),
            List.of(new Surcharge("Fuel", new BigDecimal("5.00"), false, "FUEL")),
            List.of(new TaxRate("VAT", new BigDecimal("10.00")))
        );

        Tariff.ShipmentQuote quote = new Tariff.ShipmentQuote(
            customerId,
            "NORTH",
            "CENTRAL",
            "VAN",
            "EXPRESS",
            "A",
            new BigDecimal("40"),
            new BigDecimal("100"),
            new BigDecimal("4"),
            new BigDecimal("2"),
            Instant.parse("2026-03-01T00:00:00Z")
        );

        Tariff.PriceBreakdown breakdown = tariff.calculate(quote);
        assertThat(breakdown.getBaseAmount()).isEqualByComparingTo("100.00");
        assertThat(breakdown.getSurchargeAmount()).isEqualByComparingTo("5.00");
        assertThat(breakdown.getTaxAmount()).isEqualByComparingTo("10.50");
        assertThat(breakdown.getTotalAmount()).isEqualByComparingTo("115.50");
    }

    @Test
    void invoiceAndPaymentAllocationTrackStateTransitions() {
        Invoice invoice = new Invoice(
            UUID.randomUUID(),
            "INV-00000001",
            UUID.randomUUID(),
            "USD",
            List.of(new InvoiceLine(UUID.randomUUID(), "Service", new BigDecimal("2"), new BigDecimal("50"), "VAT")),
            new BigDecimal("100"),
            new BigDecimal("10"),
            new BigDecimal("110"),
            Instant.now(),
            Instant.now().plusSeconds(3600),
            InvoiceState.DRAFT
        );

        invoice.issue();
        Payment payment = new Payment(UUID.randomUUID(), invoice.getCustomerId(), "CASH", new BigDecimal("60"), "USD", "PAY-1", Instant.now());
        payment.allocate(new PaymentAllocation(payment.getId(), invoice.getId(), new BigDecimal("60")));
        invoice.applyAllocation(new PaymentAllocation(payment.getId(), invoice.getId(), new BigDecimal("60")));

        assertThat(invoice.getState()).isEqualTo(InvoiceState.PARTIALLY_PAID);
        assertThat(invoice.getRemainingBalance()).isEqualByComparingTo("50");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.ALLOCATED);
    }
}
