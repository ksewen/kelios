package com.github.ksewen.kelios.catalog;

import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.audit.Actor;
import com.github.ksewen.kelios.audit.AuditService;
import com.github.ksewen.kelios.tenancy.TenantService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    public enum Kind {
        CATEGORY("category"), MANUFACTURER("manufacturer"), PRODUCT("product");
        private final String table;
        Kind(String table) { this.table=table; }
    }
    public enum MergeKind { CATEGORY, MANUFACTURER }
    public static final UUID UNKNOWN_CATEGORY=UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID UNKNOWN_MANUFACTURER=UUID.fromString("00000000-0000-0000-0000-000000000002");
    public record Entry(UUID id, String name, UUID tenantId, boolean deleted, UUID categoryId, UUID manufacturerId, String manufacturerNumber) {
        public boolean global() { return tenantId==null; }
        public Map<String,Object> snapshot() {
            Map<String,Object> result=new LinkedHashMap<>();result.put("id",id.toString());result.put("name",name);
            result.put("tenantId",tenantId==null?null:tenantId.toString());
            result.put("visibility",global()?"GLOBAL":"TENANT");result.put("deleted",deleted);
            if(categoryId!=null) {
                result.put("categoryId",categoryId.toString());result.put("manufacturerId",manufacturerId.toString());
                result.put("manufacturerNumber",manufacturerNumber);
            }
            return result;
        }
    }
    public record MergePair(Kind kind, Entry source, Entry target) {}
    private final JdbcTemplate jdbc;
    private final CatalogUsage usage;
    private final TenantService tenants;
    private final AuditService audit;
    private final Clock clock;
    public CatalogService(JdbcTemplate jdbc, CatalogUsage usage, TenantService tenants, AuditService audit, Clock clock) {
        this.jdbc=jdbc;this.usage=usage;this.tenants=tenants;this.audit=audit;this.clock=clock;
    }
    private Entry load(Kind kind, UUID id, String locking) {return load(kind,id,locking,null);}
    private Entry load(Kind kind, UUID id, String locking,UUID onlyTenant) {
        if(id==null) throw new BusinessFailure("INVALID_INPUT","Reference ID required");
        var args=new ArrayList<Object>();args.add(id);if(onlyTenant!=null)args.add(onlyTenant);
        var rows=jdbc.query("SELECT * FROM "+kind.table+" WHERE id=?"+(onlyTenant==null?"":" AND (tenant_id IS NULL OR tenant_id=?)")+locking,(rs,n)->new Entry(
            rs.getObject("id",UUID.class),rs.getString("name"),rs.getObject("tenant_id",UUID.class),rs.getTimestamp("deleted_at")!=null,
            kind==Kind.PRODUCT?rs.getObject("category_id",UUID.class):null,kind==Kind.PRODUCT?rs.getObject("manufacturer_id",UUID.class):null,
            kind==Kind.PRODUCT?rs.getString("manufacturer_number"):null),args.toArray());
        if(rows.isEmpty()) throw new BusinessFailure("NOT_FOUND","Catalog entry not found");return rows.getFirst();
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Entry share(Kind kind, UUID id) { return load(kind,id," FOR SHARE"); }
    @Transactional(propagation=Propagation.MANDATORY)
    public Entry forUpdate(Kind kind, UUID id) { return load(kind,id," FOR UPDATE"); }
    @Transactional public Entry scoped(Kind kind,UUID id,UUID onlyTenant,boolean write){return load(kind,id,write?" FOR UPDATE":"",onlyTenant);}
    @Transactional(readOnly=true)
    public Entry read(Kind kind, UUID id) { return load(kind,id,""); }
    public void requireVisible(Entry entry, UUID tenantId, boolean newAssignment) {
        if(!entry.global() && !Objects.equals(entry.tenantId(),tenantId))throw new BusinessFailure(newAssignment && tenantId!=null?"NOT_FOUND":"INVALID_DEPENDENCY","Catalog dependency is not visible in the target scope");
        if(newAssignment && entry.deleted())throw new BusinessFailure("DELETED_TARGET","Deleted Catalog entry");
    }
    private static String required(String value) {
        if(value==null || value.isBlank())throw new BusinessFailure("INVALID_INPUT","Name required");return value.strip();
    }
    private static String optional(String value) { return value==null || value.isBlank()?null:value.strip(); }
    private void protect(Kind kind, UUID id) {
        if((kind==Kind.CATEGORY && UNKNOWN_CATEGORY.equals(id)) || (kind==Kind.MANUFACTURER && UNKNOWN_MANUFACTURER.equals(id)))
            throw new BusinessFailure("PROTECTED_UNKNOWN","Protected UNKNOWN");
    }
    private boolean references(Kind kind, UUID id) {
        if(usage.assetReferences(kind,id))return true;
        if(kind==Kind.PRODUCT)return false;
        String column=kind==Kind.CATEGORY?"category_id":"manufacturer_id";
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM product WHERE "+column+"=?)",Boolean.class,id));
    }
    @Transactional public UUID createCategory(UUID tenantId, String name, Actor actor) { return createLabel(Kind.CATEGORY,tenantId,name,actor); }
    @Transactional public UUID createManufacturer(UUID tenantId, String name, Actor actor) { return createLabel(Kind.MANUFACTURER,tenantId,name,actor); }
    private UUID createLabel(Kind kind, UUID tenantId, String name, Actor actor) {
        if(tenantId!=null)tenants.require(tenantId);UUID id=UUID.randomUUID();String clean=required(name);
        jdbc.update("INSERT INTO "+kind.table+"(id,name,visibility,tenant_id) VALUES(?,?,?,?)",id,clean,tenantId==null?"GLOBAL":"TENANT",tenantId);
        audit.record(actor,tenantId,kind+"_CREATED",kind.name(),id,Map.of("after",load(kind,id,"").snapshot()));return id;
    }
    @Transactional public UUID createProduct(UUID tenantId, UUID categoryId, UUID manufacturerId, String name, String number, Actor actor) {
        if(tenantId!=null)tenants.require(tenantId);
        requireVisible(share(Kind.CATEGORY,categoryId),tenantId,true);requireVisible(share(Kind.MANUFACTURER,manufacturerId),tenantId,true);
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO product(id,name,manufacturer_number,category_id,manufacturer_id,visibility,tenant_id) VALUES(?,?,?,?,?,?,?)",
            id,required(name),optional(number),categoryId,manufacturerId,tenantId==null?"GLOBAL":"TENANT",tenantId);
        audit.record(actor,tenantId,"PRODUCT_CREATED","PRODUCT",id,Map.of("after",load(Kind.PRODUCT,id,"").snapshot()));return id;
    }
    @Transactional public void editLabel(Kind kind, UUID id, String name, Actor actor) {
        if(kind==Kind.PRODUCT)throw new BusinessFailure("INVALID_INPUT","Use Product edit");
        Entry entry=load(kind,id," FOR UPDATE");protect(kind,id);if(entry.deleted())throw new BusinessFailure("DELETED_TARGET","Entry deleted");
        if(references(kind,id))throw new BusinessFailure("REFERENCED","Resolve retained references first");
        jdbc.update("UPDATE "+kind.table+" SET name=? WHERE id=?",required(name),id);
        audit.record(actor,entry.tenantId(),kind+"_EDITED",kind.name(),id,Map.of("before",entry.snapshot(),"after",load(kind,id,"").snapshot()));
    }
    @Transactional public void editProduct(UUID id, UUID categoryId, UUID manufacturerId, String name, String number, Actor actor) {
        Entry entry=load(Kind.PRODUCT,id," FOR UPDATE");
        if(entry.deleted())throw new BusinessFailure("DELETED_TARGET","Product deleted");
        if(usage.productEverUsed(id))throw new BusinessFailure("PRODUCT_EDIT_FROZEN","Product has historical Asset usage");
        share(Kind.CATEGORY,entry.categoryId());share(Kind.MANUFACTURER,entry.manufacturerId());
        requireVisible(share(Kind.CATEGORY,categoryId),entry.tenantId(),true);requireVisible(share(Kind.MANUFACTURER,manufacturerId),entry.tenantId(),true);
        jdbc.update("UPDATE product SET name=?,manufacturer_number=?,category_id=?,manufacturer_id=? WHERE id=?",required(name),optional(number),categoryId,manufacturerId,id);
        audit.record(actor,entry.tenantId(),"PRODUCT_EDITED","PRODUCT",id,Map.of("before",entry.snapshot(),"after",load(Kind.PRODUCT,id,"").snapshot()));
    }
    @Transactional public void delete(Kind kind, UUID id, Actor actor) {
        Entry entry=load(kind,id," FOR UPDATE");protect(kind,id);
        if(entry.deleted())throw new BusinessFailure("DELETED_TARGET","Entry deleted");
        if(references(kind,id))throw new BusinessFailure("REFERENCED","Resolve retained references first");
        jdbc.update("UPDATE "+kind.table+" SET deleted_at=?,deleted_by=? WHERE id=?",Timestamp.from(Instant.now(clock)),actor.id(),id);
        audit.record(actor,entry.tenantId(),kind+"_DELETED",kind.name(),id,Map.of("before",entry.snapshot(),"after",load(kind,id,"").snapshot()));
    }
    @Transactional public void publish(Kind kind, UUID id, Actor actor) {
        Entry entry=load(kind,id," FOR UPDATE");
        if(entry.deleted())throw new BusinessFailure("DELETED_TARGET","Entry deleted");
        if(entry.global())throw new BusinessFailure("INVALID_DEPENDENCY","Entry is already GLOBAL");
        if(kind==Kind.PRODUCT) {
            requireVisible(share(Kind.CATEGORY,entry.categoryId()),null,true);
            requireVisible(share(Kind.MANUFACTURER,entry.manufacturerId()),null,true);
        }
        jdbc.update("UPDATE "+kind.table+" SET visibility='GLOBAL',tenant_id=NULL WHERE id=?",id);
        audit.record(actor,entry.tenantId(),kind+"_PUBLISHED",kind.name(),id,Map.of("before",entry.snapshot(),"after",load(kind,id,"").snapshot()));
    }
    /** Called only by the inventory Merge coordinator in its transaction. */
    @Transactional(propagation=Propagation.MANDATORY)
    public MergePair lockMerge(MergeKind mergeKind, UUID sourceId, UUID targetId) {
        Kind kind=Kind.valueOf(mergeKind.name());if(Objects.equals(sourceId,targetId))throw new BusinessFailure("INVALID_INPUT","Distinct Merge IDs required");protect(kind,sourceId);
        for(UUID id:java.util.stream.Stream.of(sourceId,targetId).sorted().toList())load(kind,id," FOR UPDATE");
        Entry source=load(kind,sourceId,"");Entry target=load(kind,targetId,"");
        if(source.deleted() || target.deleted())throw new BusinessFailure("DELETED_TARGET","Deleted Merge entry");return new MergePair(kind,source,target);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void redirectProducts(MergePair pair) {
        String column=pair.kind()==Kind.CATEGORY?"category_id":"manufacturer_id";
        List<UUID> ids=jdbc.query("SELECT id FROM product WHERE "+column+"=? ORDER BY id FOR UPDATE",(rs,n)->rs.getObject(1,UUID.class),pair.source().id());
        for(UUID id:ids) {
            Entry product=load(Kind.PRODUCT,id,"");
            UUID existing=pair.kind()==Kind.CATEGORY?product.categoryId():product.manufacturerId();
            if(!existing.equals(pair.source().id()))continue;
            requireVisible(pair.target(),product.tenantId(),false);
            // Existing retained references may themselves be deleted; visibility must still hold.
            requireVisible(share(Kind.CATEGORY,pair.kind()==Kind.CATEGORY?pair.target().id():product.categoryId()),product.tenantId(),false);
            requireVisible(share(Kind.MANUFACTURER,pair.kind()==Kind.MANUFACTURER?pair.target().id():product.manufacturerId()),product.tenantId(),false);
            jdbc.update("UPDATE product SET "+column+"=? WHERE id=?",pair.target().id(),id);
        }
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void finishMerge(MergePair pair, Actor actor) {
        jdbc.update("UPDATE "+pair.kind().table+" SET deleted_at=?,deleted_by=? WHERE id=?",Timestamp.from(Instant.now(clock)),actor.id(),pair.source().id());
        audit.record(actor,pair.source().tenantId(),pair.kind()+"_MERGED",pair.kind().name(),pair.source().id(),Map.of("source",pair.source().snapshot(),"target",pair.target().snapshot()));
    }
    @Transactional(readOnly=true)
    public com.github.ksewen.kelios.platform.Page<Map<String,Object>> list(Kind kind,UUID tenant,boolean globalOnly,Map<String,String> q) {
        com.github.ksewen.kelios.platform.ListInput.keys(q,"deleted","visibility");
        int page=com.github.ksewen.kelios.platform.ListInput.page(q),size=com.github.ksewen.kelios.platform.ListInput.size(q);
        var args=new ArrayList<Object>();StringBuilder where=new StringBuilder(" WHERE true");
        if(globalOnly)where.append(" AND tenant_id IS NULL");else{where.append(" AND (tenant_id IS NULL OR tenant_id=?)");args.add(tenant);}
        if(q.containsKey("visibility"))switch(q.get("visibility")){case "GLOBAL"->where.append(" AND visibility='GLOBAL'");case "TENANT"->where.append(" AND visibility='TENANT'");default->throw BusinessFailure.invalid("Invalid visibility filter");}
        String deleted=q.getOrDefault("deleted","false");switch(deleted){case "false"->where.append(" AND deleted_at IS NULL");case "true"->where.append(" AND deleted_at IS NOT NULL");case "all"->{}default->throw BusinessFailure.invalid("Invalid deletion filter");}
        if(q.containsKey("q")){where.append(" AND name ILIKE ? ESCAPE '\\'");args.add(com.github.ksewen.kelios.platform.ListInput.search(q));}
        long count=jdbc.queryForObject("SELECT count(*) FROM "+kind.table+where,Long.class,args.toArray());args.add(size);args.add((long)page*size);
        return new com.github.ksewen.kelios.platform.Page<>(jdbc.query("SELECT * FROM "+kind.table+where+" ORDER BY name,id LIMIT ? OFFSET ?",com.github.ksewen.kelios.platform.JdbcRows.MAP,args.toArray()),page,size,count);
    }
    public record MergePreview(long productCount,long assetCount,List<Map<String,Object>> affectedTenants,List<String> blockers) {}
    @Transactional(readOnly=true)
    public MergePreview preview(MergeKind mergeKind,UUID sourceId,UUID targetId) {
        Kind kind=Kind.valueOf(mergeKind.name());Entry source=read(kind,sourceId),target=read(kind,targetId);var blockers=new ArrayList<String>();
        if(sourceId.equals(targetId))blockers.add("Source and target must differ");
        if(source.deleted()||target.deleted())blockers.add("Deleted endpoint");
        if(sourceId.equals(UNKNOWN_CATEGORY)||sourceId.equals(UNKNOWN_MANUFACTURER))blockers.add("Protected UNKNOWN source");
        String column=kind==Kind.CATEGORY?"category_id":"manufacturer_id";
        long products=jdbc.queryForObject("SELECT count(*) FROM product WHERE "+column+"=?",Long.class,sourceId);
        long assets=jdbc.queryForObject("SELECT count(*) FROM asset WHERE "+column+"=?",Long.class,sourceId);
        var tenants=jdbc.query("SELECT tenant_id,count(*) AS reference_count FROM (SELECT tenant_id FROM product WHERE "+column+"=? UNION ALL SELECT tenant_id FROM asset WHERE "+column+"=?) r GROUP BY tenant_id ORDER BY tenant_id",com.github.ksewen.kelios.platform.JdbcRows.MAP,sourceId,sourceId);
        if(!target.global() && tenants.stream().anyMatch(t->!Objects.equals(t.get("tenantId"),target.tenantId())))blockers.add("Target is not visible to every retained dependent");
        return new MergePreview(products,assets,tenants,blockers);
    }

}
