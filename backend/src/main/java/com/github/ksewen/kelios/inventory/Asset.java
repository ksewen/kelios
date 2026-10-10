package com.github.ksewen.kelios.inventory;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "asset")
public class Asset {
    protected Asset() {}
    @Id
    @Column(name = "id")
    UUID id;
    @Column(name = "tenant_id", updatable = false)
    UUID tenantId;
    @Column(name = "asset_tag", updatable = false, columnDefinition = "text")
    String assetTag;
    @Column(name = "category_id")
    UUID categoryId;
    @Column(name = "manufacturer_id")
    UUID manufacturerId;
    @Column(name = "product_id")
    UUID productId;
    @Column(name = "local_identifier", columnDefinition = "text")
    String localIdentifier;
    @Column(name = "serial_number", columnDefinition = "text")
    String serialNumber;
    @Column(name = "external_inventory_number", columnDefinition = "text")
    String externalInventoryNumber;
    @Column(name = "location_id")
    UUID locationId;
    @Column(name = "owner_id")
    UUID ownerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle")
    LifecycleState lifecycle;
    @Enumerated(EnumType.STRING)
    @Column(name = "condition")
    ConditionState condition;
    @Column(name = "loanable")
    boolean loanable;
    @Column(name = "loaned_to", columnDefinition = "text")
    String loanedTo;
    @Column(name = "loaned_at")
    Instant loanedAt;
    @Column(name = "expected_return_at")
    Instant expectedReturnAt;
    @Column(name = "loan_note", columnDefinition = "text")
    String loanNote;
    @Column(name = "note", columnDefinition = "text")
    String note;
    @Version
    @Column(name = "version")
    long version;
    @Column(name = "created_at")
    Instant createdAt;
    @Column(name = "created_by")
    UUID createdBy;
    @Column(name = "updated_at")
    Instant updatedAt;
    @Column(name = "updated_by")
    UUID updatedBy;
    @Column(name = "deleted_at")
    Instant deletedAt;
    @Column(name = "deleted_by")
    UUID deletedBy;
    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String assetTag() { return assetTag; }
    public UUID categoryId() { return categoryId; }
    public UUID manufacturerId() { return manufacturerId; }
    public UUID productId() { return productId; }
    public String localIdentifier() { return localIdentifier; }
    public String serialNumber() { return serialNumber; }
    public String externalInventoryNumber() { return externalInventoryNumber; }
    public UUID locationId() { return locationId; }
    public UUID ownerId() { return ownerId; }
    public LifecycleState lifecycle() { return lifecycle; }
    public ConditionState condition() { return condition; }
    public boolean loanable() { return loanable; }
    public String loanedTo() { return loanedTo; }
    public Instant loanedAt() { return loanedAt; }
    public Instant expectedReturnAt() { return expectedReturnAt; }
    public String loanNote() { return loanNote; }
    public String note() { return note; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public UUID createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
    public UUID updatedBy() { return updatedBy; }
    public Instant deletedAt() { return deletedAt; }
    public UUID deletedBy() { return deletedBy; }
}
