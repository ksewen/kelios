package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.identity.UserService.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IdentityStore {
    private final JdbcTemplate jdbc;
    public IdentityStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Identity load(UUID id,String lock){
        var rows=jdbc.query("SELECT * FROM app_user WHERE id=?"+lock,(rs,n)->new Identity(rs.getObject("id",UUID.class),Scope.valueOf(rs.getString("scope")),
            rs.getObject("tenant_id",UUID.class),Role.valueOf(rs.getString("role")),Status.valueOf(rs.getString("status")),rs.getString("display_name")),id);
        if(rows.isEmpty())throw new AuthFailure(404,"User not found");return rows.getFirst();
    }
    public void insert(Identity u){jdbc.update("INSERT INTO app_user(id,scope,tenant_id,role,status,display_name) VALUES(?,?,?,?,?,?)",u.id(),u.scope().name(),u.tenantId(),u.role().name(),u.status().name(),u.displayName());}
    public Identity managed(Identity actor,UUID id,String lock){
        Authorization.administrator(actor);if(actor.globalAdmin())return load(id,lock);
        var rows=jdbc.query("SELECT * FROM app_user WHERE id=? AND scope='TENANT' AND tenant_id=? AND role='USER'"+lock,
            (rs,n)->new Identity(rs.getObject("id",UUID.class),Scope.valueOf(rs.getString("scope")),rs.getObject("tenant_id",UUID.class),Role.valueOf(rs.getString("role")),Status.valueOf(rs.getString("status")),rs.getString("display_name")),id,actor.tenantId());
        if(rows.isEmpty())throw new AuthFailure(404,"User not found in administrative scope");return rows.getFirst();
    }
    public void recoveryGuard(){jdbc.queryForObject("SELECT id FROM global_admin_recovery_guard WHERE id=1 FOR UPDATE",Integer.class);}
    public List<Identity> list(UUID tenant){
        return jdbc.query("SELECT * FROM app_user"+(tenant==null?"":" WHERE scope='TENANT' AND tenant_id=? AND role='USER'")+" ORDER BY id",
            (rs,n)->new Identity(rs.getObject("id",UUID.class),Scope.valueOf(rs.getString("scope")),rs.getObject("tenant_id",UUID.class),Role.valueOf(rs.getString("role")),Status.valueOf(rs.getString("status")),rs.getString("display_name")),tenant==null?new Object[0]:new Object[]{tenant});
    }
}
