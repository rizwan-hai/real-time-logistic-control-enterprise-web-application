package com.example.logistics.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class Tariff {
    private final UUID id;
    private final String name;
    private final String currency;
    private final List<TariffRule> rules;
    private final List<Surcharge> surcharges;
    private final List<TaxRate> taxRates;

    public Tariff(UUID id, String name, String currency, List<TariffRule> rules, List<Surcharge> surcharges, List<TaxRate> taxRates) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.name = name == null ? "Default tariff" : name;
        this.currency = currency == null ? "USD" : currency.toUpperCase(Locale.ROOT);
        this.rules = List.copyOf(rules == null ? List.of() : rules);
        this.surcharges = List.copyOf(surcharges == null ? List.of() : surcharges);
        this.taxRates = List.copyOf(taxRates == null ? List.of() : taxRates);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCurrency() { return currency; }
    public List<TariffRule> getRules() { return rules; }
    public List<Surcharge> getSurcharges() { return surcharges; }
    public List<TaxRate> getTaxRates() { return taxRates; }

    public PriceBreakdown calculate(ShipmentQuote shipment) {
        BigDecimal eligibleBase = BigDecimal.ZERO;
        TariffRule selected = null;
        for (TariffRule rule : rules) {
            if (rule.matches(shipment)) {
                if (selected == null || rule.getPriority() > selected.getPriority()) {
                    selected = rule;
                }
            }
        }
        if (selected != null) {
            eligibleBase = selected.calculateCharge(shipment);
        }

        BigDecimal minCharge = BigDecimal.ZERO;
        if (selected != null && selected.getMinimumCharge() != null) {
            minCharge = selected.getMinimumCharge();
            if (eligibleBase.compareTo(minCharge) < 0) {
                eligibleBase = minCharge;
            }
        }

        BigDecimal surchargeTotal = BigDecimal.ZERO;
        LinkedHashMap<String, BigDecimal> surchargeBreakdown = new LinkedHashMap<>();
        for (Surcharge surcharge : surcharges) {
            BigDecimal amount = surcharge.calculateAppliedAmount(eligibleBase, shipment);
            if (amount.compareTo(BigDecimal.ZERO) != 0) {
                surchargeBreakdown.put(surcharge.getName(), amount);
                surchargeTotal = surchargeTotal.add(amount);
            }
        }

        BigDecimal taxableBase = eligibleBase.add(surchargeTotal);
        BigDecimal taxTotal = BigDecimal.ZERO;
        LinkedHashMap<String, BigDecimal> taxBreakdown = new LinkedHashMap<>();
        for (TaxRate taxRate : taxRates) {
            BigDecimal amount = taxRate.calculateTax(taxableBase);
            if (amount.compareTo(BigDecimal.ZERO) != 0) {
                taxBreakdown.put(taxRate.getName(), amount);
                taxTotal = taxTotal.add(amount);
            }
        }

        BigDecimal total = eligibleBase.add(surchargeTotal).add(taxTotal).setScale(2, RoundingMode.HALF_UP);
        PriceBreakdown breakdown = new PriceBreakdown(eligibleBase.setScale(2, RoundingMode.HALF_UP), surchargeTotal.setScale(2, RoundingMode.HALF_UP), taxTotal.setScale(2, RoundingMode.HALF_UP), total, Map.copyOf(surchargeBreakdown), Map.copyOf(taxBreakdown), Instant.now());
        return breakdown;
    }

    public static final class ShipmentQuote {
        private final UUID customerId;
        private final String originZone;
        private final String destinationZone;
        private final String vehicleType;
        private final String serviceLevel;
        private final String productClass;
        private final BigDecimal distanceKm;
        private final BigDecimal weightKg;
        private final BigDecimal volumeM3;
        private final BigDecimal pallets;
        private final Instant validOn;

        public ShipmentQuote(UUID customerId, String originZone, String destinationZone, String vehicleType,
                            String serviceLevel, String productClass,
                            BigDecimal distanceKm, BigDecimal weightKg, BigDecimal volumeM3,
                            BigDecimal pallets, Instant validOn) {
            this.customerId = customerId;
            this.originZone = originZone;
            this.destinationZone = destinationZone;
            this.vehicleType = vehicleType;
            this.serviceLevel = serviceLevel;
            this.productClass = productClass;
            this.distanceKm = distanceKm == null ? BigDecimal.ZERO : distanceKm;
            this.weightKg = weightKg == null ? BigDecimal.ZERO : weightKg;
            this.volumeM3 = volumeM3 == null ? BigDecimal.ZERO : volumeM3;
            this.pallets = pallets == null ? BigDecimal.ZERO : pallets;
            this.validOn = validOn == null ? Instant.now() : validOn;
        }

        public UUID getCustomerId() { return customerId; }
        public String getOriginZone() { return originZone; }
        public String getDestinationZone() { return destinationZone; }
        public String getVehicleType() { return vehicleType; }
        public String getServiceLevel() { return serviceLevel; }
        public String getProductClass() { return productClass; }
        public BigDecimal getDistanceKm() { return distanceKm; }
        public BigDecimal getWeightKg() { return weightKg; }
        public BigDecimal getVolumeM3() { return volumeM3; }
        public BigDecimal getPallets() { return pallets; }
        public Instant getValidOn() { return validOn; }
    }

    public static final class PriceBreakdown {
        private final BigDecimal baseAmount;
        private final BigDecimal surchargeAmount;
        private final BigDecimal taxAmount;
        private final BigDecimal totalAmount;
        private final Map<String, BigDecimal> surchargeBreakdown;
        private final Map<String, BigDecimal> taxBreakdown;
        private final Instant snapshotTime;

        public PriceBreakdown(BigDecimal baseAmount, BigDecimal surchargeAmount, BigDecimal taxAmount,
                             BigDecimal totalAmount, Map<String, BigDecimal> surchargeBreakdown,
                             Map<String, BigDecimal> taxBreakdown, Instant snapshotTime) {
            this.baseAmount = baseAmount;
            this.surchargeAmount = surchargeAmount;
            this.taxAmount = taxAmount;
            this.totalAmount = totalAmount;
            this.surchargeBreakdown = Map.copyOf(surchargeBreakdown == null ? Map.of() : surchargeBreakdown);
            this.taxBreakdown = Map.copyOf(taxBreakdown == null ? Map.of() : taxBreakdown);
            this.snapshotTime = snapshotTime == null ? Instant.now() : snapshotTime;
        }

        public BigDecimal getBaseAmount() { return baseAmount; }
        public BigDecimal getSurchargeAmount() { return surchargeAmount; }
        public BigDecimal getTaxAmount() { return taxAmount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public Map<String, BigDecimal> getSurchargeBreakdown() { return surchargeBreakdown; }
        public Map<String, BigDecimal> getTaxBreakdown() { return taxBreakdown; }
        public Instant getSnapshotTime() { return snapshotTime; }

        public boolean isImmutable() { return true; }
    }
}
