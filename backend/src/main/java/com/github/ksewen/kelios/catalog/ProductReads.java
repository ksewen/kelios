package com.github.ksewen.kelios.catalog;

import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.platform.Page;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Product-specific authorized SQL projection; lightweight association joins, no entity/API sharing. */
@Repository
public class ProductReads {
    private final JdbcTemplate jdbc;
    public ProductReads(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String FROM = " FROM product p LEFT JOIN tenant t ON t.id=p.tenant_id"
        + " JOIN category c ON c.id=p.category_id JOIN manufacturer m ON m.id=p.manufacturer_id";
    private static final String FIELDS = "p.id,p.name,p.manufacturer_number,p.visibility,p.tenant_id,t.name AS tenant_name,"
        + "p.category_id,c.name AS category_name,p.manufacturer_id,m.name AS manufacturer_name,"
        + "p.created_by,p.created_at,p.updated_by,p.updated_at,p.deleted_at,p.deleted_by";
    private record Row(UUID id, String name, String number, String visibility, UUID tenantId, String tenantName,
            UUID categoryId, String categoryName, UUID manufacturerId, String manufacturerName,
            UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt, Instant deletedAt, UUID deletedBy) {
        static Row read(ResultSet r) throws SQLException {
            return new Row(r.getObject("id",UUID.class),r.getString("name"),r.getString("manufacturer_number"),r.getString("visibility"),
                r.getObject("tenant_id",UUID.class),r.getString("tenant_name"),r.getObject("category_id",UUID.class),r.getString("category_name"),
                r.getObject("manufacturer_id",UUID.class),r.getString("manufacturer_name"),r.getObject("created_by",UUID.class),
                r.getTimestamp("created_at").toInstant(),r.getObject("updated_by",UUID.class),r.getTimestamp("updated_at").toInstant(),
                r.getTimestamp("deleted_at")==null?null:r.getTimestamp("deleted_at").toInstant(),r.getObject("deleted_by",UUID.class));
        }
        ProductModel model() {
            return new ProductModel(id,name,number,ProductModel.Visibility.valueOf(visibility),
                tenantId==null?null:new ProductModel.Reference(tenantId,tenantName),new ProductModel.Reference(categoryId,categoryName),
                new ProductModel.Reference(manufacturerId,manufacturerName),new ProductModel.Metadata(createdBy,createdAt,updatedBy,updatedAt),deletedAt,deletedBy);
        }
    }
    ProductModel detail(UUID id, UUID onlyTenant) {
        var args=new ArrayList<Object>(); args.add(id);
        String scope="";
        if(onlyTenant!=null){scope=" AND (p.tenant_id IS NULL OR p.tenant_id=?)";args.add(onlyTenant);}
        var rows=jdbc.query("SELECT "+FIELDS+FROM+" WHERE p.id=?"+scope,(r,n)->Row.read(r).model(),args.toArray());
        if(rows.isEmpty())throw BusinessFailure.missing();
        return rows.getFirst();
    }
    private static String literal(String s) { return "%"+s.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"; }
    Page<ProductModel> list(UUID effectiveTenant, ProductQuery q) {
        var args=new ArrayList<Object>();var where=new StringBuilder(" WHERE p.deleted_at IS NULL");
        if(effectiveTenant==null)where.append(" AND p.tenant_id IS NULL");
        else {where.append(" AND (p.tenant_id IS NULL OR p.tenant_id=?)");args.add(effectiveTenant);}
        if(q.visibility()!=null){where.append(" AND p.visibility=?");args.add(q.visibility().name());}
        if(q.search()!=null){where.append(" AND (p.name ILIKE ? ESCAPE '\\' OR p.manufacturer_number ILIKE ? ESCAPE '\\')");args.add(literal(q.search()));args.add(literal(q.search()));}
        if(q.categoryId()!=null){where.append(" AND p.category_id=?");args.add(q.categoryId());}
        if(q.manufacturerId()!=null){where.append(" AND p.manufacturer_id=?");args.add(q.manufacturerId());}
        if(q.manufacturerNumber()!=null){where.append(" AND p.manufacturer_number ILIKE ? ESCAPE '\\'");args.add(literal(q.manufacturerNumber()));}
        String field=q.sort()==null?"p.created_at":switch(q.sort()){case NAME->"p.name";case MANUFACTURER_NUMBER->"p.manufacturer_number";case VISIBILITY->"p.visibility";};
        String direction=q.sort()==null?"DESC":q.direction().name();
        long count=jdbc.queryForObject("SELECT count(*) FROM product p"+where,Long.class,args.toArray());
        args.add(q.size());args.add((long)q.page()*q.size());
        // NULL numbers last in both directions; retained SQL ID ties are an implementation detail, not a Domain mandate.
        var rows=jdbc.query("SELECT "+FIELDS+FROM+where+" ORDER BY "+field+" "+direction+" NULLS LAST,p.id "+direction+" LIMIT ? OFFSET ?",
            (r,n)->Row.read(r).model(),args.toArray());
        return new Page<>(rows,q.page(),q.size(),count);
    }
}
