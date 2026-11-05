package com.example.logistics.identity.application;

import java.util.Optional;
import java.util.UUID;

/** Feature modules implement this port to resolve resource ownership without exposing repositories to identity. */
public interface ScopedResourceResolver {
    Optional<ScopeDescriptor> resolve(UUID resourceId, String resourceType);
}
