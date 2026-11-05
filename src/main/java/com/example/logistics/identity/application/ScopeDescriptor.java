package com.example.logistics.identity.application;

import java.util.UUID;

public record ScopeDescriptor(UUID organizationId, UUID customerId, UUID driverId, UUID warehouseId) {}
