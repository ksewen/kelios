package com.github.ksewen.kelios.inventory;

import java.time.Instant;
import java.util.UUID;
import com.github.ksewen.kelios.platform.BusinessFailure;

/** Application projection for the two Tenant-owned flat Place resources; no transport/JPA dependency. */
public record PlaceModel(UUID id, String name, String note, Tenant tenant, Metadata metadata,
        Instant deletedAt, UUID deletedBy) {
    public record Tenant(UUID id, String name) {}
    public record Metadata(UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt,
            String createdByDisplayName, String updatedByDisplayName) {}
    public record Command(String name, String note) {
        public Command {
            if(name==null || name.isBlank())throw BusinessFailure.invalid("Name required");
            name=name.strip();note=note==null || note.isBlank()?null:note.strip();
        }
    }
}
