package com.github.ksewen.kelios.tenancy;

import java.time.Instant;
import java.util.UUID;

/** Safe application resource, independent of HTTP and the persistence entity. */
public record TenantModel(UUID id,String name,Metadata metadata) {
    public record Metadata(UUID createdBy,Instant createdAt,UUID updatedBy,Instant updatedAt) {}
}
