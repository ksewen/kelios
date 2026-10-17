package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.audit.SystemActor;
import com.github.ksewen.kelios.catalog.CatalogLabel;
import com.github.ksewen.kelios.catalog.CatalogLabelQuery;
import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.platform.ListInput;
import com.github.ksewen.kelios.platform.Page;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Endpoint-owned HTTP DTOs and explicit boundary mappings; never persisted. */
final class CatalogLabelDtos {
    private CatalogLabelDtos() {}
    record Created(UUID id) {}
    record NameCommand(String name) {
        @com.fasterxml.jackson.annotation.JsonCreator
        public static NameCommand bind(@com.fasterxml.jackson.annotation.JsonProperty("name") Object name) {
            if (!(name instanceof String value)) throw BusinessFailure.invalid("Name text required");
            return new NameCommand(value);
        }
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void reject(String key, Object value) { throw BusinessFailure.invalid("Unknown command field"); }
        CatalogLabel.Name model() { return new CatalogLabel.Name(name); }
    }
    record MergeCommand(UUID sourceId, UUID targetId) {
        @com.fasterxml.jackson.annotation.JsonCreator
        public static MergeCommand bind(@com.fasterxml.jackson.annotation.JsonProperty("sourceId") Object source,
                @com.fasterxml.jackson.annotation.JsonProperty("targetId") Object target) {
            return new MergeCommand(required(source), required(target));
        }
        private static UUID required(Object value) {
            if (!(value instanceof String text)) throw BusinessFailure.invalid("UUID text required");
            var id = ListInput.uuid(text); if (id == null) throw BusinessFailure.invalid("Reference required"); return id;
        }
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void reject(String key, Object value) { throw BusinessFailure.invalid("Unknown command field"); }
    }
    static CatalogLabelQuery query(Map<String, String> input, CatalogLabel.Kind kind) {
        ListInput.keys(input, "visibility", "sort", "direction");
        // Controller supplies resource-specific defaults; unrelated resources retain their separately gated contract.
        int page = ListInput.page(Map.of("page", input.getOrDefault("page", "0")));
        int size = ListInput.size(Map.of("size", input.getOrDefault("size", "20")));
        CatalogLabel.Visibility visibility = null;
        if (input.containsKey("visibility")) {
            try { visibility = CatalogLabel.Visibility.valueOf(input.get("visibility")); }
            catch (IllegalArgumentException ex) { throw BusinessFailure.invalid("Invalid visibility filter"); }
        }
        CatalogLabelQuery.Sort sort = input.containsKey("sort") ? switch (input.get("sort")) {
            case "name" -> CatalogLabelQuery.Sort.NAME; case "visibility" -> CatalogLabelQuery.Sort.VISIBILITY;
            case "tenantName" -> {
                if (kind != CatalogLabel.Kind.CATEGORY) throw BusinessFailure.invalid("Unsupported Catalog sort");
                yield CatalogLabelQuery.Sort.TENANT_NAME;
            }
            default -> throw BusinessFailure.invalid("Unsupported Catalog sort");
        } : null;
        CatalogLabelQuery.Direction direction = input.containsKey("direction") ? switch (input.get("direction")) {
            case "asc" -> CatalogLabelQuery.Direction.ASC; case "desc" -> CatalogLabelQuery.Direction.DESC;
            default -> throw BusinessFailure.invalid("Invalid sort direction");
        } : null;
        return new CatalogLabelQuery(page, size, input.get("q"), visibility, sort, direction);
    }
    record Tenant(UUID id, String name) {}
    record Metadata(UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt,
            String createdByDisplayName, String updatedByDisplayName) {}
    record Item(UUID id, String name, String visibility, UUID tenantId, Tenant tenant, Metadata metadata) {}
    record Detail(UUID id, String name, String visibility, UUID tenantId, Tenant tenant,
            boolean deleted, Instant deletedAt, UUID deletedBy, Metadata metadata) {}
    record AffectedTenant(UUID tenantId, long referenceCount) {}
    record Preview(long productCount, long assetCount, List<AffectedTenant> affectedTenants, List<String> blockers) {}
    private static Tenant tenant(CatalogLabel m) { return m.tenant() == null ? null : new Tenant(m.tenant().id(), m.tenant().name()); }
    private static UUID tenantId(CatalogLabel m) { return m.tenant() == null ? null : m.tenant().id(); }
    private static Metadata metadata(CatalogLabel m) {
        var a = m.metadata(); return new Metadata(a.createdBy(), a.createdAt(), a.updatedBy(), a.updatedAt(),
            a.createdByDisplayName() == null ? SystemActor.displayName(a.createdBy()) : a.createdByDisplayName(),
            a.updatedByDisplayName() == null ? SystemActor.displayName(a.updatedBy()) : a.updatedByDisplayName());
    }
    static Page<Item> page(Page<CatalogLabel> page) {
        return new Page<>(page.items().stream().map(m -> new Item(m.id(), m.name(), m.visibility().name(), tenantId(m), tenant(m), metadata(m))).toList(),
            page.page(), page.size(), page.totalElements());
    }
    static Detail detail(CatalogLabel m) {
        return new Detail(m.id(), m.name(), m.visibility().name(), tenantId(m), tenant(m), m.deletedAt() != null,
            m.deletedAt(), m.deletedBy(), metadata(m));
    }
    static Preview preview(CatalogLabel.Preview p) {
        return new Preview(p.productCount(), p.assetCount(), p.affectedTenants().stream()
            .map(t -> new AffectedTenant(t.tenantId(), t.referenceCount())).toList(), p.blockers());
    }
}
