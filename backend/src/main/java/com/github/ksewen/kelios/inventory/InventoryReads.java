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
    public AssetRead detail(UUID id,UUID onlyTenant){
        var args=new ArrayList<Object>(List.of(id));String scope="";if(onlyTenant!=null){scope=" AND a.tenant_id=?";args.add(onlyTenant);}
        var rows=jdbc.query(SELECT+" WHERE a.id=? AND a.deleted_at IS NULL"+scope,InventoryReads::assetRow,args.toArray());
        if(rows.isEmpty())throw BusinessFailure.missing();return rows.getFirst();
    }
    public Page<AssetRead> list(UUID tenant,AssetQuery q){
        var args=new ArrayList<Object>();StringBuilder where=new StringBuilder(" WHERE a.deleted_at IS NULL");
        if(tenant!=null){where.append(" AND a.tenant_id=?");args.add(tenant);}
        Object[][] filters={{"lifecycle",q.lifecycle()==null?null:q.lifecycle().name()},{"condition",q.condition()==null?null:q.condition().name()},
            {"location_id",q.locationId()},{"owner_id",q.ownerId()},{"category_id",q.categoryId()},{"manufacturer_id",q.manufacturerId()},{"product_id",q.productId()}};
        String[] referenceKeys={"locationId","ownerId","categoryId","manufacturerId","productId"};
        UUID[] references={q.locationId(),q.ownerId(),q.categoryId(),q.manufacturerId(),q.productId()};
        for(int i=0;i<references.length;i++)if(references[i]!=null)validateFilter(tenant,referenceKeys[i],references[i]);
        for(Object[] f:filters)if(f[1]!=null){where.append(" AND a.").append(f[0]).append("=?");args.add(f[1]);}
        if(q.loaned()!=null)where.append(q.loaned()?" AND a.loaned_to IS NOT NULL":" AND a.loaned_to IS NULL");
        if(q.loanable()!=null){where.append(" AND a.loanable=?");args.add(q.loanable());}
        if(q.search()!=null){where.append(" AND (a.asset_tag ILIKE ? ESCAPE '\\' OR a.serial_number ILIKE ? ESCAPE '\\' OR a.local_identifier ILIKE ? ESCAPE '\\' OR a.external_inventory_number ILIKE ? ESCAPE '\\')");String search=literal(q.search());args.addAll(List.of(search,search,search,search));}
        String[][] text={{"local_identifier",q.localIdentifier()},{"serial_number",q.serialNumber()},{"external_inventory_number",q.externalInventoryNumber()}};
        for(String[] f:text)if(f[1]!=null){where.append(" AND a.").append(f[0]).append(" ILIKE ? ESCAPE '\\'");args.add(literal(f[1]));}
        String sort=switch(q.sort()){case ASSET_TAG->"a.asset_tag";case CREATED_AT->"a.created_at";case LIFECYCLE->"a.lifecycle";case CONDITION->"a.condition";};
        String direction=q.direction().name();
        long count=jdbc.queryForObject("SELECT count(*) FROM asset a"+where,Long.class,args.toArray());args.add(q.size());args.add((long)q.page()*q.size());
        // Existing SQL identity tie retained for repeatable pages; not a new Domain sort capability.
        return new Page<>(jdbc.query(SELECT+where+" ORDER BY "+sort+" "+direction+",a.id "+direction+" LIMIT ? OFFSET ?",InventoryReads::assetRow,args.toArray()),q.page(),q.size(),count);
    }
    private static String literal(String value){return "%"+value.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
    private void validateFilter(UUID tenant,String key,UUID id){
        String table=switch(key){case "locationId"->"location";case "ownerId"->"owner";case "categoryId"->"category";case "manufacturerId"->"manufacturer";case "productId"->"product";default->throw new AssertionError();};
        var args=new ArrayList<Object>(List.of(id));String scope="";
        if(tenant!=null){scope=key.equals("locationId")||key.equals("ownerId")?" AND tenant_id=?":" AND (tenant_id IS NULL OR tenant_id=?)";args.add(tenant);}
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE id=?"+scope+")",Boolean.class,args.toArray())))throw BusinessFailure.missing();
    }
    public Page<AssetRead.Event> events(UUID id,UUID onlyTenant,AssetHistoryQuery q){
        UUID tenant=tenantOf(id,onlyTenant);long count=jdbc.queryForObject("SELECT count(*) FROM asset_event WHERE asset_id=? AND tenant_id=?",Long.class,id,tenant);
        return new Page<>(jdbc.query("SELECT * FROM asset_event WHERE asset_id=? AND tenant_id=? ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",InventoryReads::eventRow,id,tenant,q.size(),(long)q.page()*q.size()),q.page(),q.size(),count);
    }
    private record Row(AssetService.View state,AssetRead.Reference tenant,AssetRead.Reference category,
            AssetRead.Reference manufacturer,AssetRead.Reference product,AssetRead.Reference location,AssetRead.Reference owner) {
        AssetRead model(){return new AssetRead(state,tenant,category,manufacturer,product,location,owner);}
    }
    private static java.time.Instant instant(java.sql.ResultSet r,String field)throws java.sql.SQLException{
        var value=r.getTimestamp(field);return value==null?null:value.toInstant();
    }
    private static AssetRead.Reference reference(java.sql.ResultSet r,String kind)throws java.sql.SQLException{
        UUID id=r.getObject(kind+"_id",UUID.class);return id==null?null:new AssetRead.Reference(id,r.getString(kind+"_name"));
    }
    private static AssetRead assetRow(java.sql.ResultSet r,int index)throws java.sql.SQLException{
        var state=new AssetService.View(r.getObject("id",UUID.class),r.getObject("tenant_id",UUID.class),r.getString("asset_tag"),
            r.getObject("category_id",UUID.class),r.getObject("manufacturer_id",UUID.class),r.getObject("product_id",UUID.class),r.getObject("location_id",UUID.class),r.getObject("owner_id",UUID.class),
            LifecycleState.valueOf(r.getString("lifecycle")),ConditionState.valueOf(r.getString("condition")),r.getBoolean("loanable"),r.getString("serial_number"),r.getString("external_inventory_number"),r.getString("note"),r.getString("local_identifier"),r.getLong("version"),instant(r,"deleted_at"),
            r.getString("loaned_to"),instant(r,"loaned_at"),instant(r,"expected_return_at"),r.getString("loan_note"),instant(r,"created_at"),r.getObject("created_by",UUID.class),instant(r,"updated_at"),r.getObject("updated_by",UUID.class));
        return new Row(state,reference(r,"tenant"),reference(r,"category"),reference(r,"manufacturer"),reference(r,"product"),reference(r,"location"),reference(r,"owner")).model();
    }
    private static final tools.jackson.databind.json.JsonMapper JSON=tools.jackson.databind.json.JsonMapper.builder().build();
    private static AssetRead.Event eventRow(java.sql.ResultSet r,int index)throws java.sql.SQLException{
        var data=JSON.readValue(r.getString("data"),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});
        return new AssetRead.Event(r.getObject("id",UUID.class),r.getObject("asset_id",UUID.class),r.getObject("tenant_id",UUID.class),r.getString("type"),instant(r,"occurred_at"),instant(r,"created_at"),r.getObject("actor_id",UUID.class),r.getString("actor_display_name"),data,r.getString("note"));
    }
}
