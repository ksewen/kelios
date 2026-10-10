package com.github.ksewen.kelios.inventory;
import com.github.ksewen.kelios.platform.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal scoped SQL reads. The secured application adapter supplies authorized operating scope. */
@Service
@Transactional(readOnly=true)
public class InventoryReads {
    private final JdbcTemplate jdbc;
    public InventoryReads(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static final String SELECT="SELECT a.*,c.name AS category_name,m.name AS manufacturer_name,p.name AS product_name,l.name AS location_name,o.name AS owner_name,t.name AS tenant_name FROM asset a JOIN category c ON c.id=a.category_id JOIN manufacturer m ON m.id=a.manufacturer_id LEFT JOIN product p ON p.id=a.product_id JOIN location l ON l.id=a.location_id LEFT JOIN owner o ON o.id=a.owner_id JOIN tenant t ON t.id=a.tenant_id";
    public UUID tenantOf(UUID id,UUID onlyTenant){
        var args=new ArrayList<Object>(List.of(id));String scope="";if(onlyTenant!=null){scope=" AND tenant_id=?";args.add(onlyTenant);}
        var rows=jdbc.query("SELECT tenant_id FROM asset WHERE id=? AND deleted_at IS NULL"+scope,(rs,n)->rs.getObject(1,UUID.class),args.toArray());
        if(rows.isEmpty())throw BusinessFailure.missing();return rows.getFirst();
    }
    public Map<String,Object> detail(UUID id,UUID onlyTenant){
        var args=new ArrayList<Object>(List.of(id));String scope="";if(onlyTenant!=null){scope=" AND a.tenant_id=?";args.add(onlyTenant);}
        var rows=jdbc.query(SELECT+" WHERE a.id=? AND a.deleted_at IS NULL"+scope,JdbcRows.MAP,args.toArray());
        if(rows.isEmpty())throw BusinessFailure.missing();return rows.getFirst();
    }
    public Page<Map<String,Object>> list(UUID tenant,Map<String,String> q){
        ListInput.keys(q,"sort","direction","lifecycle","condition","locationId","ownerId","categoryId","manufacturerId","productId","loaned");
        int page=ListInput.page(q),size=ListInput.size(q);var args=new ArrayList<Object>();StringBuilder where=new StringBuilder(" WHERE a.deleted_at IS NULL");
        if(tenant!=null){where.append(" AND a.tenant_id=?");args.add(tenant);}
        String[][] filters={{"lifecycle","lifecycle"},{"condition","condition"},{"locationId","location_id"},{"ownerId","owner_id"},{"categoryId","category_id"},{"manufacturerId","manufacturer_id"},{"productId","product_id"}};
        for(String[] f:filters)if(q.containsKey(f[0])){
            Object value;
            if(f[0].equals("lifecycle")){try{value=LifecycleState.valueOf(q.get(f[0])).name();}catch(IllegalArgumentException ex){throw BusinessFailure.invalid("Invalid lifecycle filter");}}
            else if(f[0].equals("condition")){try{value=ConditionState.valueOf(q.get(f[0])).name();}catch(IllegalArgumentException ex){throw BusinessFailure.invalid("Invalid condition filter");}}
            else {value=ListInput.uuid(q.get(f[0]));validateFilter(tenant,f[0],(UUID)value);}
            where.append(" AND a.").append(f[1]).append("=?");args.add(value);
        }
        if(q.containsKey("loaned"))where.append(ListInput.bool(q.get("loaned"))?" AND a.loaned_to IS NOT NULL":" AND a.loaned_to IS NULL");
        if(q.containsKey("q")){where.append(" AND (a.asset_tag ILIKE ? ESCAPE '\\' OR a.serial_number ILIKE ? ESCAPE '\\' OR a.local_identifier ILIKE ? ESCAPE '\\' OR a.external_inventory_number ILIKE ? ESCAPE '\\')");String search=ListInput.search(q);args.addAll(List.of(search,search,search,search));}
        String sort=switch(q.getOrDefault("sort","createdAt")){case "tag"->"a.asset_tag";case "createdAt"->"a.created_at";case "lifecycle"->"a.lifecycle";case "condition"->"a.condition";default->throw BusinessFailure.invalid("Unsupported sort");};
        String direction=q.getOrDefault("direction","desc");if(!Set.of("asc","desc").contains(direction))throw BusinessFailure.invalid("Unsupported sort direction");
        long count=jdbc.queryForObject("SELECT count(*) FROM asset a"+where,Long.class,args.toArray());args.add(size);args.add((long)page*size);
        return new Page<>(jdbc.query(SELECT+where+" ORDER BY "+sort+" "+direction+",a.id "+direction+" LIMIT ? OFFSET ?",JdbcRows.MAP,args.toArray()),page,size,count);
    }
    private void validateFilter(UUID tenant,String key,UUID id){
        String table=switch(key){case "locationId"->"location";case "ownerId"->"owner";case "categoryId"->"category";case "manufacturerId"->"manufacturer";case "productId"->"product";default->throw new AssertionError();};
        var args=new ArrayList<Object>(List.of(id));String scope="";
        if(tenant!=null){scope=key.equals("locationId")||key.equals("ownerId")?" AND tenant_id=?":" AND (tenant_id IS NULL OR tenant_id=?)";args.add(tenant);}
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE id=?"+scope+")",Boolean.class,args.toArray())))throw BusinessFailure.missing();
    }
    public Page<Map<String,Object>> events(UUID id,UUID onlyTenant,Map<String,String> q){
        ListInput.keys(q);if(q.containsKey("q"))throw BusinessFailure.invalid("History has no text search");int page=ListInput.page(q),size=ListInput.size(q);
        UUID tenant=tenantOf(id,onlyTenant);long count=jdbc.queryForObject("SELECT count(*) FROM asset_event WHERE asset_id=? AND tenant_id=?",Long.class,id,tenant);
        return new Page<>(jdbc.query("SELECT * FROM asset_event WHERE asset_id=? AND tenant_id=? ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",JdbcRows.MAP,id,tenant,size,(long)page*size),page,size,count);
    }
}
