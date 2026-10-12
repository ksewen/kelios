package com.github.ksewen.kelios.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/** Safe read projection, independent of a live User lookup. Human names are not reconstructed. */
public record BusinessMetadata(UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt) {
    @com.fasterxml.jackson.annotation.JsonProperty
    public String createdByDisplayName() { return SystemActor.displayName(createdBy); }
    @com.fasterxml.jackson.annotation.JsonProperty
    public String updatedByDisplayName() { return SystemActor.displayName(updatedBy); }
    public static BusinessMetadata read(ResultSet row) throws SQLException {
        return new BusinessMetadata(row.getObject("created_by", UUID.class), row.getTimestamp("created_at").toInstant(),
            row.getObject("updated_by", UUID.class), row.getTimestamp("updated_at").toInstant());
    }
}
