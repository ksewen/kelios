package com.github.ksewen.kelios.catalog;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "product")
public class Product {
    protected Product() {}
    @Id
    @Column(name = "id")
    UUID id;
    @Column(name = "name", columnDefinition = "text")
    String name;
    @Column(name = "visibility", columnDefinition = "text")
    String visibility;
    @Column(name = "tenant_id")
    UUID tenantId;
    @Column(name = "category_id")
    UUID categoryId;
    @Column(name = "manufacturer_id")
    UUID manufacturerId;
    @Column(name = "manufacturer_number", columnDefinition = "text")
    String manufacturerNumber;
    @Column(name = "deleted_at")
    Instant deletedAt;
    @Column(name = "deleted_by")
    UUID deletedBy;
    public UUID id() { return id; }
    public String name() { return name; }
    public String visibility() { return visibility; }
    public UUID tenantId() { return tenantId; }
    public UUID categoryId() { return categoryId; }
    public UUID manufacturerId() { return manufacturerId; }
    public String manufacturerNumber() { return manufacturerNumber; }
    public Instant deletedAt() { return deletedAt; }
    public UUID deletedBy() { return deletedBy; }
}
