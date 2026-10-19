package com.github.ksewen.kelios.tenancy;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;


@Entity
@Table(name = "tenant")
public class Tenant {
    protected Tenant() {}
    @Id
    @Column(name = "id")
    UUID id;
    @Column(name = "name", columnDefinition = "text")
    String name;
    @Column(name = "created_by", nullable = false)
    UUID createdBy;
    public UUID createdBy() { return createdBy; }
    @Column(name = "created_by_display_name", updatable = false, columnDefinition = "text")
    String createdByDisplayName;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
    public Instant createdAt() { return createdAt; }
    @Column(name = "updated_by", nullable = false)
    UUID updatedBy;
    public UUID updatedBy() { return updatedBy; }
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
    public Instant updatedAt() { return updatedAt; }
    public UUID id() { return id; }
    public String name() { return name; }
}
