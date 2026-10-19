package com.github.ksewen.kelios.catalog;

import java.time.Instant;
import java.util.UUID;
import com.github.ksewen.kelios.platform.BusinessFailure;

/** Product application projection; independent of persistence and transport serialization. */
public record ProductModel(UUID id, String name, String manufacturerNumber, Visibility visibility,
        Reference tenant, Reference category, Reference manufacturer, Metadata metadata,
        Instant deletedAt, UUID deletedBy) {
    public enum Visibility { GLOBAL, TENANT }
    public record Reference(UUID id, String name) {}
    public record Metadata(UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt, String createdByDisplayName) {
        public Metadata(UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt) {this(createdBy,createdAt,updatedBy,updatedAt,null);}
    }
    public record Command(String name, UUID categoryId, UUID manufacturerId, String manufacturerNumber) {
        public Command {
            if (name == null || name.isBlank()) throw BusinessFailure.invalid("Name required");
            if (categoryId == null || manufacturerId == null) throw BusinessFailure.invalid("Catalog references required");
            name = name.strip();
            manufacturerNumber = manufacturerNumber == null || manufacturerNumber.isBlank() ? null : manufacturerNumber.strip();
        }
    }
}
