package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.identity.UserService.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IdentityStore {
    private final JdbcTemplate jdbc;
    private final java.time.Clock clock;
    public IdentityStore(JdbcTemplate jdbc,java.time.Clock clock){this.jdbc=jdbc;this.clock=clock;}
    public Identity load(UUID id,String lock){
        var rows=jdbc.query("SELECT * FROM app_user WHERE id=?"+lock,(rs,n)->new Identity(rs.getObject("id",UUID.class),Scope.valueOf(rs.getString("scope")),
            rs.getObject("tenant_id",UUID.class),Role.valueOf(rs.getString("role")),Status.valueOf(rs.getString("status")),rs.getString("display_name")),id);
        if(rows.isEmpty())throw new AuthFailure(404,"User not found");return rows.getFirst();
    }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void insert(Identity u,com.github.ksewen.kelios.audit.Actor actor){
        var now=java.sql.Timestamp.from(java.time.Instant.now(clock));
        jdbc.update("INSERT INTO app_user(id,scope,tenant_id,role,status,display_name,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",u.id(),u.scope().name(),u.tenantId(),u.role().name(),u.status().name(),u.displayName(),actor.id(),now,actor.id(),now);
    }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void touch(UUID id,com.github.ksewen.kelios.audit.Actor actor){
        jdbc.update("UPDATE app_user SET updated_by=?,updated_at=? WHERE id=?",actor.id(),java.sql.Timestamp.from(java.time.Instant.now(clock)),id);
    }
    public Identity managed(Identity actor,UUID id,String lock){
        Authorization.administrator(actor);if(actor.globalAdmin())return load(id,lock);
        var rows=jdbc.query("SELECT * FROM app_user WHERE id=? AND scope='TENANT' AND tenant_id=? AND role='USER'"+lock,
            (rs,n)->new Identity(rs.getObject("id",UUID.class),Scope.valueOf(rs.getString("scope")),rs.getObject("tenant_id",UUID.class),Role.valueOf(rs.getString("role")),Status.valueOf(rs.getString("status")),rs.getString("display_name")),id,actor.tenantId());
        if(rows.isEmpty())throw new AuthFailure(404,"User not found in administrative scope");return rows.getFirst();
    }
    public void recoveryGuard(){jdbc.queryForObject("SELECT id FROM global_admin_recovery_guard WHERE id=1 FOR UPDATE",Integer.class);}
    private static final String READ="SELECT u.id,u.scope,u.tenant_id,u.role,u.status,u.display_name,u.created_by,u.created_at,u.updated_by,u.updated_at,t.name AS tenant_name FROM app_user u LEFT JOIN tenant t ON t.id=u.tenant_id";
    /** Persistence read row: raw storage values mapped explicitly into the application projection. */
    private record Row(UUID id,String scope,UUID tenantId,String role,String status,String displayName,String tenantName,
            UUID createdBy,java.time.Instant createdAt,UUID updatedBy,java.time.Instant updatedAt) {
        com.github.ksewen.kelios.identity.UserModel model(){
            var tenant=tenantId==null?null:new com.github.ksewen.kelios.identity.UserModel.Tenant(tenantId,tenantName);
            var metadata=new com.github.ksewen.kelios.identity.UserModel.Metadata(createdBy,createdAt,updatedBy,updatedAt);
            return new com.github.ksewen.kelios.identity.UserModel(id,Scope.valueOf(scope),Role.valueOf(role),Status.valueOf(status),displayName,tenant,metadata);
        }
    }
    private static com.github.ksewen.kelios.identity.UserModel row(java.sql.ResultSet r,int index)throws java.sql.SQLException{
        return new Row(r.getObject("id",UUID.class),r.getString("scope"),r.getObject("tenant_id",UUID.class),r.getString("role"),r.getString("status"),r.getString("display_name"),r.getString("tenant_name"),
            r.getObject("created_by",UUID.class),r.getTimestamp("created_at").toInstant(),r.getObject("updated_by",UUID.class),r.getTimestamp("updated_at").toInstant()).model();
    }
    public com.github.ksewen.kelios.identity.UserModel detail(UUID id,UUID forcedTenant){
        var args=new ArrayList<Object>(List.of(id));String scope="";
        if(forcedTenant!=null){scope=" AND u.scope='TENANT' AND u.tenant_id=?";args.add(forcedTenant);}
        var rows=jdbc.query(READ+" WHERE u.id=?"+scope,IdentityStore::row,args.toArray());
        if(rows.isEmpty())throw new AuthFailure(404,"User not found in administrative scope");return rows.getFirst();
    }
    public com.github.ksewen.kelios.platform.Page<com.github.ksewen.kelios.identity.UserModel> list(UUID forcedTenant,com.github.ksewen.kelios.identity.UserQuery q){
        var args=new ArrayList<Object>();var where=new StringBuilder(" WHERE 1=1");
        if(forcedTenant!=null){where.append(" AND u.scope='TENANT' AND u.tenant_id=?");args.add(forcedTenant);}
        // The TENANT transport Tenant field has no authority and is not compared as proof.
        else if(q.tenantId()!=null){where.append(" AND u.tenant_id=?");args.add(q.tenantId());}
        if(q.scope()!=null){where.append(" AND u.scope=?");args.add(q.scope().name());}
        if(q.role()!=null){where.append(" AND u.role=?");args.add(q.role().name());}
        if(q.status()!=null){where.append(" AND u.status=?");args.add(q.status().name());}
        if(q.search()!=null){where.append(" AND u.display_name ILIKE ? ESCAPE '\\'");args.add("%"+q.search().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");}
        String sort=q.sort()==null?"u.created_at":switch(q.sort()){case DISPLAY_NAME->"u.display_name";case SCOPE->"u.scope";case ROLE->"u.role";case STATUS->"u.status";};
        String direction=q.sort()==null?"DESC":q.direction().name();
        long count=jdbc.queryForObject("SELECT count(*) FROM app_user u"+where,Long.class,args.toArray());args.add(q.size());args.add((long)q.page()*q.size());
        return new com.github.ksewen.kelios.platform.Page<>(jdbc.query(READ+where+" ORDER BY "+sort+" "+direction+",u.id "+direction+" LIMIT ? OFFSET ?",IdentityStore::row,args.toArray()),q.page(),q.size(),count);
    }
}
