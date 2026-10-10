package com.github.ksewen.kelios.inventory;

import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.audit.Actor;
import com.github.ksewen.kelios.audit.AuditService;
import com.github.ksewen.kelios.tenancy.TenantService;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceService {
    public enum Kind { LOCATION, OWNER }
    public record Place(UUID id, UUID tenantId, String name, boolean deleted) {
        public Map<String,Object> snapshot() { return Map.of("id",id.toString(),"tenantId",tenantId.toString(),"name",name); }
    }
    private final JdbcTemplate jdbc;
    private final TenantService tenants;
    private final AuditService audit;
    private final Clock clock;
    public PlaceService(JdbcTemplate jdbc,TenantService tenants,AuditService audit,Clock clock) {this.jdbc=jdbc;this.tenants=tenants;this.audit=audit;this.clock=clock;}
    private String table(Kind kind) {return kind==Kind.LOCATION?"location":"owner";}
    private String required(String value) {if(value==null||value.isBlank())throw new BusinessFailure("INVALID_INPUT","Name required");return value.strip();}
    private Place load(Kind kind,UUID tenant,UUID id,String lock) {
        var rows=jdbc.query("SELECT * FROM "+table(kind)+" WHERE id=? AND tenant_id=?"+lock,(rs,n)->new Place(rs.getObject("id",UUID.class),rs.getObject("tenant_id",UUID.class),rs.getString("name"),rs.getTimestamp("deleted_at")!=null),id,tenant);
        if(rows.isEmpty())throw new BusinessFailure("NOT_FOUND","Place not found in Tenant");return rows.getFirst();
    }
    @Transactional(readOnly=true) public Place read(Kind kind,UUID id) {
        var rows=jdbc.query("SELECT * FROM "+table(kind)+" WHERE id=?",(rs,n)->new Place(rs.getObject("id",UUID.class),rs.getObject("tenant_id",UUID.class),rs.getString("name"),rs.getTimestamp("deleted_at")!=null),id);
        if(rows.isEmpty())throw new BusinessFailure("NOT_FOUND","Place not found");return rows.getFirst();
    }
    @Transactional(readOnly=true) public Place readScoped(Kind kind,UUID id,UUID onlyTenant){return onlyTenant==null?read(kind,id):load(kind,onlyTenant,id,"");}
    @Transactional(propagation=Propagation.MANDATORY)
    public Place share(Kind kind,UUID tenant,UUID id,boolean newAssignment) {
        Place p=load(kind,tenant,id," FOR SHARE");if(newAssignment && p.deleted())throw new BusinessFailure("DELETED_TARGET","Deleted Place");return p;
    }
    @Transactional public UUID create(Kind kind,UUID tenant,String name,Actor actor) {
        tenants.require(tenant);UUID id=UUID.randomUUID();jdbc.update("INSERT INTO "+table(kind)+"(id,tenant_id,name) VALUES(?,?,?)",id,tenant,required(name));
        audit.record(actor,tenant,kind+"_CREATED",kind.name(),id,Map.of("name",required(name)));return id;
    }
    private boolean referenced(Kind kind,UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM asset WHERE "+table(kind)+"_id=?)",Boolean.class,id));
    }
    @Transactional public void edit(Kind kind,UUID tenant,UUID id,String name,Actor actor) {
        Place p=load(kind,tenant,id," FOR UPDATE");if(p.deleted())throw new BusinessFailure("DELETED_TARGET","Place deleted");
        if(kind==Kind.OWNER && referenced(kind,id))throw new BusinessFailure("REFERENCED","Resolve retained Asset references first");
        jdbc.update("UPDATE "+table(kind)+" SET name=? WHERE id=?",required(name),id);
        audit.record(actor,p.tenantId(),kind+"_EDITED",kind.name(),id,Map.of("before",p.snapshot(),"name",required(name)));
    }
    @Transactional public void delete(Kind kind,UUID tenant,UUID id,Actor actor) {
        Place p=load(kind,tenant,id," FOR UPDATE");if(p.deleted())throw new BusinessFailure("DELETED_TARGET","Place deleted");
        if(referenced(kind,id))throw new BusinessFailure("REFERENCED","Resolve retained Asset references first");
        jdbc.update("UPDATE "+table(kind)+" SET deleted_at=?,deleted_by=? WHERE id=?",Timestamp.from(Instant.now(clock)),actor.id(),id);
        audit.record(actor,p.tenantId(),kind+"_DELETED",kind.name(),id,Map.of("before",p.snapshot()));
    }
    @Transactional(readOnly=true)
    public com.github.ksewen.kelios.platform.Page<Map<String,Object>> list(Kind kind,UUID tenant,Map<String,String> q) {
        com.github.ksewen.kelios.platform.ListInput.keys(q,"deleted");int page=com.github.ksewen.kelios.platform.ListInput.page(q),size=com.github.ksewen.kelios.platform.ListInput.size(q);
        var args=new java.util.ArrayList<Object>();args.add(tenant);StringBuilder where=new StringBuilder(" WHERE tenant_id=?");
        switch(q.getOrDefault("deleted","false")){case "false"->where.append(" AND deleted_at IS NULL");case "true"->where.append(" AND deleted_at IS NOT NULL");case "all"->{}default->throw BusinessFailure.invalid("Invalid deletion filter");}
        if(q.containsKey("q")){where.append(" AND name ILIKE ? ESCAPE '\\'");args.add(com.github.ksewen.kelios.platform.ListInput.search(q));}
        long count=jdbc.queryForObject("SELECT count(*) FROM "+table(kind)+where,Long.class,args.toArray());args.add(size);args.add((long)page*size);
        return new com.github.ksewen.kelios.platform.Page<>(jdbc.query("SELECT * FROM "+table(kind)+where+" ORDER BY name,id LIMIT ? OFFSET ?",com.github.ksewen.kelios.platform.JdbcRows.MAP,args.toArray()),page,size,count);
    }

}
