package com.github.ksewen.kelios.inventory;

import jakarta.persistence.*;
import java.util.UUID;
import java.util.Map;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "asset_event")
@org.hibernate.annotations.Immutable
public class AssetEvent {
    protected AssetEvent() {}
    @Id
    @Column(name = "id")
    UUID id;
    @Column(name = "asset_id")
    UUID assetId;
    @Column(name = "tenant_id")
    UUID tenantId;
    @Column(name = "type", columnDefinition = "text")
    String type;
    @Column(name = "occurred_at")
    Instant occurredAt;
    @Column(name = "created_at")
    Instant createdAt;
    @Column(name = "actor_id")
    UUID actorId;
    @Column(name = "actor_display_name", columnDefinition = "text")
    String actorDisplayName;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data")
    Map<String, Object> data;
    @Column(name = "note", columnDefinition = "text")
    String note;
    public UUID id() { return id; }
    public UUID assetId() { return assetId; }
    public UUID tenantId() { return tenantId; }
    public String type() { return type; }
    public Instant occurredAt() { return occurredAt; }
    public Instant createdAt() { return createdAt; }
    public UUID actorId() { return actorId; }
    public String actorDisplayName() { return actorDisplayName; }
    public Map<String, Object> data() { return data; }
    public String note() { return note; }
}
