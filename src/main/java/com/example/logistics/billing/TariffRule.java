package com.example.logistics.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class TariffRule {
    private final UUID id;
    private final String name;
    private final PricingModel pricingModel;
    private final BigDecimal unitPrice;
    private final BigDecimal minimumCharge;
    private final BigDecimal distanceUnit;
    private final Map<BigDecimal, BigDecimal> tieredPricing;
    private final UUID customerId;
    private final String originZone;
    private final String destinationZone;
    private final String vehicleType;
    private final String serviceLevel;
    private final String productClass;
    private final Instant validFrom;
    private final Instant validTo;
    private final int priority;

    public TariffRule(UUID id, String name, PricingModel pricingModel, BigDecimal unitPrice, BigDecimal minimumCharge,
                     BigDecimal distanceUnit, Map<BigDecimal, BigDecimal> tieredPricing,
                     UUID customerId, String originZone, String destinationZone, String vehicleType,
                     String serviceLevel, String productClass, Instant validFrom, Instant validTo, int priority) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.name = name == null ? "Rule" : name;
        this.pricingModel = pricingModel == null ? PricingModel.FLAT : pricingModel;
        this.unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        this.minimumCharge = minimumCharge == null ? BigDecimal.ZERO : minimumCharge;
        this.distanceUnit = distanceUnit == null ? BigDecimal.ONE : distanceUnit;
        this.tieredPricing = tieredPricing == null ? Map.of() : new java.util.TreeMap<>(tieredPricing);
        this.customerId = customerId;
        this.originZone = originZone;
        this.destinationZone = destinationZone;
        this.vehicleType = vehicleType;
        this.serviceLevel = serviceLevel;
        this.productClass = productClass;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.priority = priority;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public PricingModel getPricingModel() { return pricingModel; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getMinimumCharge() { return minimumCharge; }
    public BigDecimal getDistanceUnit() { return distanceUnit; }
    public Map<BigDecimal, BigDecimal> getTieredPricing() { return tieredPricing; }
    public UUID getCustomerId() { return customerId; }
    public String getOriginZone() { return originZone; }
    public String getDestinationZone() { return destinationZone; }
    public String getVehicleType() { return vehicleType; }
    public String getServiceLevel() { return serviceLevel; }
    public String getProductClass() { return productClass; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
    public int getPriority() { return priority; }

    public boolean matches(Tariff.ShipmentQuote shipment) {
        if (shipment == null) return false;
        Instant now = shipment.getValidOn();
        if (validFrom != null && now.isBefore(validFrom)) return false;
        if (validTo != null && now.isAfter(validTo)) return false;
        if (customerId != null && shipment.getCustomerId() != null && !customerId.equals(shipment.getCustomerId())) return false;
        if (customerId != null && shipment.getCustomerId() == null) return false;
        if (originZone != null && !originZone.equalsIgnoreCase(shipment.getOriginZone())) return false;
        if (destinationZone != null && !destinationZone.equalsIgnoreCase(shipment.getDestinationZone())) return false;
        if (vehicleType != null && !vehicleType.equalsIgnoreCase(shipment.getVehicleType())) return false;
        if (serviceLevel != null && !serviceLevel.equalsIgnoreCase(shipment.getServiceLevel())) return false;
        if (productClass != null && !productClass.equalsIgnoreCase(shipment.getProductClass())) return false;
        return true;
    }

    public BigDecimal calculateCharge(Tariff.ShipmentQuote shipment) {
        return switch (pricingModel) {
            case FLAT -> unitPrice;
            case PER_KILOMETER -> unitPrice.multiply(shipment.getDistanceKm().divide(distanceUnit, 4, RoundingMode.HALF_UP));
            case PER_KILOGRAM -> unitPrice.multiply(shipment.getWeightKg().divide(distanceUnit, 4, RoundingMode.HALF_UP));
            case PER_CUBIC_METER -> unitPrice.multiply(shipment.getVolumeM3().divide(distanceUnit, 4, RoundingMode.HALF_UP));
            case PER_PALLET -> unitPrice.multiply(shipment.getPallets().divide(distanceUnit, 4, RoundingMode.HALF_UP));
            case TIERED -> calculateTieredCharge(shipment);
        };
    }

    private BigDecimal calculateTieredCharge(Tariff.ShipmentQuote shipment) {
        if (tieredPricing.isEmpty()) return unitPrice;
        BigDecimal amount = unitPrice;
        var sorted = new java.util.TreeMap<>(tieredPricing);
        for (var entry : sorted.entrySet()) {
            if (shipment.getDistanceKm().compareTo(entry.getKey()) <= 0) {
                amount = entry.getValue();
                break;
            }
            amount = entry.getValue();
        }
        return amount;
    }
}
