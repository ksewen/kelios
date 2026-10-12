package com.github.ksewen.kelios.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.github.ksewen.kelios.platform.BusinessFailure;

/** Application model for the two flat Catalog resources; no HTTP/JPA/serialization concerns. */
public record CatalogLabel(UUID id, String name, Visibility visibility, Tenant tenant,
        Metadata metadata, Instant deletedAt, UUID deletedBy) {
    public enum Kind {
        CATEGORY, MANUFACTURER;
        CatalogService.Kind legacyKind() { return CatalogService.Kind.valueOf(name()); }
        CatalogService.MergeKind mergeKind() { return CatalogService.MergeKind.valueOf(name()); }
        String table() { return this == CATEGORY ? "category" : "manufacturer"; }
    }
    public enum Visibility { GLOBAL, TENANT }
    public record Tenant(UUID id, String name) {}
    public record Metadata(UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt) {}
    public record Name(String value) {
        public Name {
            if (value == null || value.isBlank()) throw BusinessFailure.invalid("Name required");
            value = value.strip();
        }
    }
    public record AffectedTenant(UUID tenantId, long referenceCount) {}
    public record Preview(long productCount, long assetCount, List<AffectedTenant> affectedTenants, List<String> blockers) {}
}
