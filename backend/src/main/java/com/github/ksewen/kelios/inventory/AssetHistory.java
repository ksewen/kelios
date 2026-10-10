package com.github.ksewen.kelios.inventory;
import com.github.ksewen.kelios.audit.Actor;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
@Service
public class AssetHistory {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final JsonMapper json=JsonMapper.builder().build();
    public AssetHistory(JdbcTemplate jdbc,Clock clock) {this.jdbc=jdbc;this.clock=clock;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void append(Asset asset,String type,Actor actor,Map<String,Object> before,Map<String,Object> after) {
        append(asset,type,actor,before,after,Map.of(),null);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void append(Asset asset,String type,Actor actor,Map<String,Object> before,Map<String,Object> after,Map<String,Object> context,String note) {
        if(!after.containsKey("productId") || (before!=null && !before.containsKey("productId")))throw new IllegalArgumentException("Product usage history required");
        Map<String,Object> data=new LinkedHashMap<>();data.put("before",before);data.put("after",after);data.putAll(context);
        Timestamp now=Timestamp.from(Instant.now(clock));
        jdbc.update("INSERT INTO asset_event(id,asset_id,tenant_id,type,occurred_at,created_at,actor_id,actor_display_name,data,note) VALUES(?,?,?,?,?,?,?,?,?::jsonb,?)",
            UUID.randomUUID(),asset.id,asset.tenantId,type,now,now,actor.id(),actor.displayName(),json.writeValueAsString(data),note);
    }
}
