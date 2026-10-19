package com.github.ksewen.kelios.tenancy;

import com.github.ksewen.kelios.tenancy.TenantModel.Metadata;
import com.github.ksewen.kelios.identity.auth.AuthFailure;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Explicit persisted projection; the application supplies authoritative scope. */
@Component
public class TenantReads {
    private final JdbcTemplate jdbc;
    public TenantReads(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private record Row(UUID id,String name,Metadata metadata){TenantModel model(){return new TenantModel(id,name,metadata);}}
    private List<TenantModel> read(String suffix,Object... args){
        return jdbc.query("SELECT id,name,created_by,created_by_display_name,created_at,updated_by,updated_at FROM tenant"+suffix,
            (r,n)->new Row(r.getObject("id",UUID.class),r.getString("name"),new Metadata(r.getObject("created_by",UUID.class),r.getTimestamp("created_at").toInstant(),r.getObject("updated_by",UUID.class),r.getTimestamp("updated_at").toInstant(),r.getString("created_by_display_name"))),args).stream().map(Row::model).toList();
    }
    public List<TenantModel> list(UUID forcedTenant){return forcedTenant==null?read(" ORDER BY created_at DESC"):read(" WHERE id=? ORDER BY created_at DESC",forcedTenant);}
    public TenantModel detail(UUID id,UUID forcedTenant){
        var rows=forcedTenant==null?read(" WHERE id=?",id):read(" WHERE id=? AND id=?",id,forcedTenant);
        if(rows.isEmpty())throw new AuthFailure(404,"Tenant not found in authorized scope");
        return rows.getFirst();
    }
}
