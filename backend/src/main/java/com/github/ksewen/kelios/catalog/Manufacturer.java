package com.github.ksewen.kelios.catalog;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "manufacturer")
public class Manufacturer {
    protected Manufacturer() {}
    @Id
    @Column(name = "id")
    UUID id;
    @Column(name = "name", columnDefinition = "text")
    String name;
    @Column(name = "visibility", columnDefinition = "text")
    String visibility;
    @Column(name = "tenant_id")
    UUID tenantId;
    @Column(name = "deleted_at")
    Instant deletedAt;
    @Column(name = "deleted_by")
    UUID deletedBy;
    public UUID id() { return id; }
    public String name() { return name; }
    public String visibility() { return visibility; }
    public UUID tenantId() { return tenantId; }
    public Instant deletedAt() { return deletedAt; }
    public UUID deletedBy() { return deletedBy; }
}
