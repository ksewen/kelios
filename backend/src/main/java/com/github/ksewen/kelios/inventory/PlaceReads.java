package com.github.ksewen.kelios.inventory;

import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.platform.Page;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Explicit Place SQL/read mapping with scoped ID selection and lightweight Tenant projection. */
@Repository
public class PlaceReads {
    private final JdbcTemplate jdbc;
    public PlaceReads(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static final String FIELDS="p.id,p.name,p.note,p.tenant_id,t.name AS tenant_name,"
        +"p.created_by,p.created_by_display_name,p.created_at,p.updated_by,p.updated_at,p.deleted_at,p.deleted_by,"
        +"ub.display_name AS updated_by_display_name";
    private static final String ACTOR_JOINS=" LEFT JOIN app_user ub ON ub.id=p.updated_by";
    private static String table(PlaceService.Kind kind){return switch(kind){case LOCATION->"location";case OWNER->"owner";};}
    private record Row(UUID id,String name,String note,UUID tenantId,String tenantName,UUID createdBy,
            Instant createdAt,UUID updatedBy,Instant updatedAt,Instant deletedAt,UUID deletedBy,
            String createdByDisplayName,String updatedByDisplayName){
        static Row read(ResultSet r)throws SQLException{
            return new Row(r.getObject("id",UUID.class),r.getString("name"),r.getString("note"),r.getObject("tenant_id",UUID.class),r.getString("tenant_name"),
                r.getObject("created_by",UUID.class),r.getTimestamp("created_at").toInstant(),r.getObject("updated_by",UUID.class),r.getTimestamp("updated_at").toInstant(),
                r.getTimestamp("deleted_at")==null?null:r.getTimestamp("deleted_at").toInstant(),r.getObject("deleted_by",UUID.class),
                r.getString("created_by_display_name"),r.getString("updated_by_display_name"));
        }
        PlaceModel model(){return new PlaceModel(id,name,note,new PlaceModel.Tenant(tenantId,tenantName),new PlaceModel.Metadata(createdBy,createdAt,updatedBy,updatedAt,createdByDisplayName,updatedByDisplayName),deletedAt,deletedBy);}
    }
    PlaceModel detail(PlaceService.Kind kind,UUID id,UUID onlyTenant){
        var args=new ArrayList<Object>();args.add(id);String scope="";
        if(onlyTenant!=null){scope=" AND p.tenant_id=?";args.add(onlyTenant);}
        var rows=jdbc.query("SELECT "+FIELDS+" FROM "+table(kind)+" p JOIN tenant t ON t.id=p.tenant_id"+ACTOR_JOINS+" WHERE p.id=?"+scope,(r,n)->Row.read(r).model(),args.toArray());
        if(rows.isEmpty())throw BusinessFailure.missing();return rows.getFirst();
    }
    Page<PlaceModel> list(PlaceService.Kind kind,UUID effectiveTenant,PlaceQuery q){
        var args=new ArrayList<Object>();var where=new StringBuilder(" WHERE p.deleted_at IS NULL");
        if(effectiveTenant!=null){where.append(" AND p.tenant_id=?");args.add(effectiveTenant);}
        if(q.search()!=null){where.append(" AND p.name ILIKE ? ESCAPE '\\'");args.add("%"+q.search().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");}
        String field=q.sort()==null?"p.created_at":"p.name",direction=q.sort()==null?"DESC":q.direction().name();
        long count=jdbc.queryForObject("SELECT count(*) FROM "+table(kind)+" p"+where,Long.class,args.toArray());args.add(q.size());args.add((long)q.page()*q.size());
        // Retained internal SQL ID ties are not a mandatory Domain sort or a client-selected field.
        var rows=jdbc.query("SELECT "+FIELDS+" FROM "+table(kind)+" p JOIN tenant t ON t.id=p.tenant_id"+ACTOR_JOINS+where+" ORDER BY "+field+" "+direction+",p.id "+direction+" LIMIT ? OFFSET ?",(r,n)->Row.read(r).model(),args.toArray());
        return new Page<>(rows,q.page(),q.size(),count);
    }
}
