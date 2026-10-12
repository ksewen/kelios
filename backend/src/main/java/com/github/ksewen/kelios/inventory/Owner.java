package com.github.ksewen.kelios.inventory;

import jakarta.persistence.*;
import java.util.UUID;

import java.time.Instant;

@Entity
@Table(name = "owner")
public class Owner {
    protected Owner() {}
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
    @Column(name = "created_by", nullable = false)
    UUID createdBy;
    public UUID createdBy() { return createdBy; }
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
    public Instant createdAt() { return createdAt; }
    @Column(name = "updated_by", nullable = false)
    UUID updatedBy;
    public UUID updatedBy() { return updatedBy; }
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
    public Instant updatedAt() { return updatedAt; }
    @Column(name = "note", columnDefinition = "text")
    String note;
    public String note() { return note; }
    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String name() { return name; }
    public Instant deletedAt() { return deletedAt; }
    public UUID deletedBy() { return deletedBy; }
}
