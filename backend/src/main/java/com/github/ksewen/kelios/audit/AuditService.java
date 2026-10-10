package com.github.ksewen.kelios.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    public AuditService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Actor actor, UUID tenantId, String action, String type, UUID target, Map<String, Object> data) {
        jdbc.update("INSERT INTO audit_log(id,tenant_id,actor_id,actor_display_name,action,target_type,target_id,occurred_at,data) VALUES(?,?,?,?,?,?,?,?,?::jsonb)",
            UUID.randomUUID(), tenantId, actor.id(), actor.displayName(), action, type, target, java.sql.Timestamp.from(Instant.now(clock)), json.writeValueAsString(data));
    }
}
