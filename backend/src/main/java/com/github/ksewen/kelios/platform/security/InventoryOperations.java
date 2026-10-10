package com.github.ksewen.kelios.platform.security;
import com.github.ksewen.kelios.audit.*;
import com.github.ksewen.kelios.catalog.CatalogService;
import com.github.ksewen.kelios.identity.auth.*;
import com.github.ksewen.kelios.identity.UserService.*;
import com.github.ksewen.kelios.inventory.*;
import com.github.ksewen.kelios.platform.*;
import com.github.ksewen.kelios.tenancy.TenantService;
import java.util.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit Phase 3 application boundary. No HTTP caller supplies actors or ownership facts. */
@Service
public class InventoryOperations {
    private final InventoryReads reads;private final AssetService assets;private final CatalogService catalog;private final PlaceService places;private final CatalogMergeService merges;private final AuditReads audits;private final TenantService tenants;
    public InventoryOperations(InventoryReads reads,AssetService assets,CatalogService catalog,PlaceService places,CatalogMergeService merges,AuditReads audits,TenantService tenants){this.reads=reads;this.assets=assets;this.catalog=catalog;this.places=places;this.merges=merges;this.audits=audits;this.tenants=tenants;}
    private void requireTenant(UUID tenant){try{tenants.require(tenant);}catch(IllegalArgumentException ex){throw BusinessFailure.missing();}}
    private void selection(UUID tenant,AssetService.CatalogSelection s){if(s instanceof AssetService.ProductSelection p)catalog.scoped(CatalogService.Kind.PRODUCT,p.productId(),tenant,false);else if(s instanceof AssetService.DirectSelection d){catalog.scoped(CatalogService.Kind.CATEGORY,d.categoryId(),tenant,false);catalog.scoped(CatalogService.Kind.MANUFACTURER,d.manufacturerId(),tenant,false);}}
    private Identity actor(){Identity a=CurrentIdentity.require();Authorization.active(a);return a;}
    private UUID onlyTenant(){Identity a=actor();return a.scope()==Scope.TENANT?a.tenantId():null;}
    private Identity writer(){Identity a=actor();Authorization.administrator(a);return a;}
    private UUID assetTenant(UUID id){writer();UUID tenant=reads.tenantOf(id,onlyTenant());if(assets.get(tenant,id).deletedAt()!=null)throw BusinessFailure.missing();return tenant;}
    @Transactional(readOnly=true) public Page<Map<String,Object>> list(UUID tenant,Map<String,String> q){Authorization.readTenant(actor(),tenant);requireTenant(tenant);return reads.list(tenant,q);}
    @Transactional(readOnly=true) public Page<Map<String,Object>> all(Map<String,String> input){Identity a=actor();if(a.scope()!=Scope.GLOBAL)throw AuthFailure.denied();var q=new LinkedHashMap<>(input);UUID tenant=ListInput.uuid(q.remove("tenantId"));if(tenant!=null)requireTenant(tenant);return reads.list(tenant,q);}
    @Transactional(readOnly=true) public Map<String,Object> detail(UUID id){return reads.detail(id,onlyTenant());}
    @Transactional(readOnly=true) public Page<Map<String,Object>> events(UUID id,Map<String,String> q){return reads.events(id,onlyTenant(),q);}
    @Transactional public AssetService.View register(UUID tenant,AssetService.Registration command){Authorization.writeTenant(actor(),tenant);requireTenant(tenant);selection(tenant,command.catalog());return assets.register(tenant,command,actor().actor());}
    @Transactional public AssetService.View text(UUID id,long version,String serial,String inventory,String note,String localIdentifier){return assets.editText(assetTenant(id),id,version,serial,inventory,note,localIdentifier,actor().actor());}
    @Transactional public AssetService.View catalog(UUID id,long version,AssetService.CatalogSelection selection){UUID tenant=assetTenant(id);selection(tenant,selection);return assets.changeCatalog(tenant,id,version,selection,actor().actor());}
    @Transactional public AssetService.View loanability(UUID id,long version,boolean loanable){return assets.changeLoanability(assetTenant(id),id,version,loanable,actor().actor());}
    @Transactional public AssetService.View lifecycle(UUID id,long version,LifecycleState target,DeregistrationReason reason,String note){return assets.lifecycle(assetTenant(id),id,version,target,reason,note,actor().actor());}
    @Transactional public AssetService.View condition(UUID id,long version,ConditionState target,String note){return assets.condition(assetTenant(id),id,version,target,note,actor().actor());}
    @Transactional public AssetService.View location(UUID id,long version,UUID target){UUID tenant=assetTenant(id);places.readScoped(PlaceService.Kind.LOCATION,target,tenant);return assets.changeLocation(tenant,id,version,target,actor().actor());}
    @Transactional public AssetService.View owner(UUID id,long version,UUID target){UUID tenant=assetTenant(id);if(target!=null)places.readScoped(PlaceService.Kind.OWNER,target,tenant);return assets.changeOwner(tenant,id,version,target,actor().actor());}
    @Transactional public AssetService.View loan(UUID id,long version,String borrower,Instant returnAt,String note){return assets.loan(assetTenant(id),id,version,borrower,returnAt,note,actor().actor());}
    @Transactional public AssetService.View returned(UUID id,long version){return assets.returned(assetTenant(id),id,version,actor().actor());}
    @Transactional(readOnly=true) public Page<Map<String,Object>> catalogList(CatalogService.Kind kind,UUID tenant,Map<String,String> q){Identity a=actor();if(tenant!=null){Authorization.readTenant(a,tenant);requireTenant(tenant);}return catalog.list(kind,tenant,tenant==null,q);}
    @Transactional(readOnly=true) public CatalogService.Entry entry(CatalogService.Kind kind,UUID id){return catalog.scoped(kind,id,onlyTenant(),false);}
    @Transactional public UUID createCategory(UUID tenant,String name){Authorization.writeCatalog(actor(),tenant);if(tenant!=null)requireTenant(tenant);return catalog.createCategory(tenant,name,actor().actor());}
    @Transactional public UUID createManufacturer(UUID tenant,String name){Authorization.writeCatalog(actor(),tenant);if(tenant!=null)requireTenant(tenant);return catalog.createManufacturer(tenant,name,actor().actor());}
    @Transactional public UUID createProduct(UUID tenant,UUID category,UUID manufacturer,String name,String number){Authorization.writeCatalog(actor(),tenant);if(tenant!=null)requireTenant(tenant);return catalog.createProduct(tenant,category,manufacturer,name,number,actor().actor());}
    private CatalogService.Entry writable(CatalogService.Kind kind,UUID id){writer();var e=catalog.scoped(kind,id,onlyTenant(),true);Authorization.writeCatalog(actor(),e.tenantId());return e;}
    @Transactional public void editCategory(UUID id,String name){writable(CatalogService.Kind.CATEGORY,id);catalog.editLabel(CatalogService.Kind.CATEGORY,id,name,actor().actor());}
    @Transactional public void editManufacturer(UUID id,String name){writable(CatalogService.Kind.MANUFACTURER,id);catalog.editLabel(CatalogService.Kind.MANUFACTURER,id,name,actor().actor());}
    @Transactional public void editProduct(UUID id,UUID category,UUID manufacturer,String name,String number){writable(CatalogService.Kind.PRODUCT,id);catalog.editProduct(id,category,manufacturer,name,number,actor().actor());}
    @Transactional public void deleteCatalog(CatalogService.Kind kind,UUID id){writable(kind,id);catalog.delete(kind,id,actor().actor());}
    @Transactional public void publish(CatalogService.Kind kind,UUID id){Authorization.globalAdmin(actor());catalog.publish(kind,id,actor().actor());}
    @Transactional public void merge(CatalogService.MergeKind kind,UUID source,UUID target){Authorization.globalAdmin(actor());merges.merge(kind,source,target,actor().actor());}
    @Transactional(readOnly=true) public CatalogService.MergePreview preview(CatalogService.MergeKind kind,UUID source,UUID target){Authorization.globalAdmin(actor());return catalog.preview(kind,source,target);}
    @Transactional(readOnly=true) public Page<Map<String,Object>> places(PlaceService.Kind kind,UUID tenant,Map<String,String> q){Authorization.readTenant(actor(),tenant);requireTenant(tenant);return places.list(kind,tenant,q);}
    @Transactional(readOnly=true) public PlaceService.Place place(PlaceService.Kind kind,UUID id){return places.readScoped(kind,id,onlyTenant());}
    @Transactional public UUID createPlace(PlaceService.Kind kind,UUID tenant,String name){Authorization.writeTenant(actor(),tenant);requireTenant(tenant);return places.create(kind,tenant,name,actor().actor());}
    @Transactional public void editPlace(PlaceService.Kind kind,UUID id,String name){writer();var p=places.readScoped(kind,id,onlyTenant());Authorization.writeTenant(actor(),p.tenantId());places.edit(kind,p.tenantId(),id,name,actor().actor());}
    @Transactional public void deletePlace(PlaceService.Kind kind,UUID id){writer();var p=places.readScoped(kind,id,onlyTenant());Authorization.writeTenant(actor(),p.tenantId());places.delete(kind,p.tenantId(),id,actor().actor());}
    private Identity auditActor(){Identity a=actor();Authorization.administrator(a);return a;}
    @Transactional(readOnly=true) public Page<Map<String,Object>> audit(Map<String,String> q){Identity a=auditActor();if(!a.globalAdmin() && (q.containsKey("tenantId")||q.containsKey("globalOnly")))throw AuthFailure.denied();return audits.list(a.tenantId(),a.globalAdmin(),q);}
    @Transactional(readOnly=true) public Map<String,Object> audit(UUID id){Identity a=auditActor();return audits.detail(id,a.tenantId(),a.globalAdmin());}
}
