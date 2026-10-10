package com.github.ksewen.kelios.audit;

import jakarta.persistence.*;
import java.util.UUID;
import java.util.Map;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
@org.hibernate.annotations.Immutable
public class AuditLog {
    protected AuditLog() {}
    @Id
    @Column(name = "id")
    UUID id;
    @Column(name = "actor_id")
    UUID actorId;
    @Column(name = "actor_display_name", columnDefinition = "text")
    String actorDisplayName;
    @Column(name = "action", columnDefinition = "text")
    String action;
    @Column(name = "target_type", columnDefinition = "text")
    String targetType;
    @Column(name = "target_id")
    UUID targetId;
    @Column(name = "occurred_at")
    Instant occurredAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data")
    Map<String, Object> data;
    @Column(name = "tenant_id", updatable = false)
    UUID tenantId;
    public UUID tenantId() { return tenantId; }
    public UUID id() { return id; }
    public UUID actorId() { return actorId; }
    public String actorDisplayName() { return actorDisplayName; }
    public String action() { return action; }
    public String targetType() { return targetType; }
    public UUID targetId() { return targetId; }
    public Instant occurredAt() { return occurredAt; }
    public Map<String, Object> data() { return data; }
}
