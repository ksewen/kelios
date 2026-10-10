package com.github.ksewen.kelios.identity;

import jakarta.persistence.*;
import java.util.UUID;

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
    public UUID id() { return id; }
    public String scope() { return scope; }
    public UUID tenantId() { return tenantId; }
    public String role() { return role; }
    public String status() { return status; }
    public String displayName() { return displayName; }
}
