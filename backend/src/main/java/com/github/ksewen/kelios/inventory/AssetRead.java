package com.github.ksewen.kelios.inventory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Application read projections, independent of HTTP serialization and JPA entities. */
public record AssetRead(AssetService.View state,Reference tenant,Reference category,Reference manufacturer,
        Reference product,Reference location,Reference owner) {
    public record Reference(UUID id,String name) {}
    public record Event(UUID id,UUID assetId,UUID tenantId,String type,Instant occurredAt,Instant createdAt,
            UUID actorId,String actorDisplayName,Map<String,Object> data,String note) {}
}
