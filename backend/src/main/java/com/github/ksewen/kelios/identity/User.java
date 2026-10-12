package com.github.ksewen.kelios.identity;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;


@Entity
@Table(name = "app_user")
public class User {
    protected User() {}
    @Id
    @Column(name = "id")
    UUID id;
    @Column(name = "scope", columnDefinition = "text")
    String scope;
    @Column(name = "tenant_id")
    UUID tenantId;
    @Column(name = "role", columnDefinition = "text")
    String role;
    @Column(name = "status", columnDefinition = "text")
    String status;
    @Column(name = "display_name", columnDefinition = "text")
    String displayName;
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
    public UUID id() { return id; }
    public String scope() { return scope; }
    public UUID tenantId() { return tenantId; }
    public String role() { return role; }
    public String status() { return status; }
    public String displayName() { return displayName; }
}
