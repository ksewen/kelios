package com.github.ksewen.kelios.inventory;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "location")
public class Location {
    protected Location() {}
    @Id
    @Column(name = "id")
    UUID id;
    @Column(name = "tenant_id")
    UUID tenantId;
    @Column(name = "name", columnDefinition = "text")
    String name;
    @Column(name = "deleted_at")
    Instant deletedAt;
    @Column(name = "deleted_by")
    UUID deletedBy;
    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String name() { return name; }
    public Instant deletedAt() { return deletedAt; }
    public UUID deletedBy() { return deletedBy; }
}
