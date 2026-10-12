package com.github.ksewen.kelios.audit;

import com.github.ksewen.kelios.platform.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly=true)
public class AuditReads {
    private final JdbcTemplate jdbc;private static final JsonMapper JSON=JsonMapper.builder().build();
    public AuditReads(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static final String COLUMNS="id,tenant_id,actor_id,actor_display_name,action,target_type,target_id,occurred_at,data";
    private record Row(UUID id,UUID tenantId,UUID actorId,String actorDisplayName,String action,String targetType,UUID targetId,java.time.Instant occurredAt,Map<String,Object> data){AuditRecord model(){return new AuditRecord(id,tenantId,actorId,actorDisplayName,action,targetType,targetId,occurredAt,data);}}
    private List<AuditRecord> read(String sql,Object... args){return jdbc.query(sql,(r,n)->new Row(r.getObject("id",UUID.class),r.getObject("tenant_id",UUID.class),r.getObject("actor_id",UUID.class),r.getString("actor_display_name"),r.getString("action"),r.getString("target_type"),r.getObject("target_id",UUID.class),r.getTimestamp("occurred_at").toInstant(),JSON.readValue(r.getString("data"),Map.class)),args).stream().map(Row::model).toList();}
    public Page<AuditRecord> list(UUID authenticatedTenant,boolean globalAdmin,AuditQuery q){
        var args=new ArrayList<Object>();StringBuilder where=new StringBuilder(" WHERE true");
        if(!globalAdmin){if(authenticatedTenant==null)throw new BusinessFailure("FORBIDDEN","Audit access denied");where.append(" AND tenant_id=?");args.add(authenticatedTenant);if(q.tenantId()!=null || q.globalOnly()!=null)throw BusinessFailure.invalid("Audit scope filters are GLOBAL ADMIN only");}
        else {if(q.tenantId()!=null){where.append(" AND tenant_id=?");args.add(q.tenantId());}if(Boolean.TRUE.equals(q.globalOnly()))where.append(" AND tenant_id IS NULL");}
        if(q.action()!=null){where.append(" AND action=?");args.add(q.action());}
        if(q.targetType()!=null){where.append(" AND target_type=?");args.add(q.targetType());}
        if(q.targetId()!=null){where.append(" AND target_id=?");args.add(q.targetId());}
        if(q.actorId()!=null){where.append(" AND actor_id=?");args.add(q.actorId());}
        if(q.from()!=null){where.append(" AND occurred_at>=?");args.add(java.sql.Timestamp.from(q.from()));}
        if(q.until()!=null){where.append(" AND occurred_at<=?");args.add(java.sql.Timestamp.from(q.until()));}
        long count=jdbc.queryForObject("SELECT count(*) FROM audit_log"+where,Long.class,args.toArray());
        args.add(q.size());args.add((long)q.page()*q.size());
        return new Page<>(read("SELECT "+COLUMNS+" FROM audit_log"+where+" ORDER BY occurred_at "+q.direction().name()+" LIMIT ? OFFSET ?",args.toArray()),q.page(),q.size(),count);
    }
    public AuditRecord detail(UUID id,UUID authenticatedTenant,boolean globalAdmin){
        var args=new ArrayList<Object>(List.of(id));String scope="";
        if(!globalAdmin){if(authenticatedTenant==null)throw new BusinessFailure("FORBIDDEN","Audit access denied");scope=" AND tenant_id=?";args.add(authenticatedTenant);}
        var rows=read("SELECT "+COLUMNS+" FROM audit_log WHERE id=?"+scope,args.toArray());if(rows.isEmpty())throw BusinessFailure.missing();return rows.getFirst();
    }
}
