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
    private final InventoryReads reads;private final AssetService assets;private final CatalogService catalog;private final PlaceService places;private final AuditReads audits;private final TenantService tenants;
    public InventoryOperations(InventoryReads reads,AssetService assets,CatalogService catalog,PlaceService places,AuditReads audits,TenantService tenants){this.reads=reads;this.assets=assets;this.catalog=catalog;this.places=places;this.audits=audits;this.tenants=tenants;}
    private void requireTenant(UUID tenant){try{tenants.require(tenant);}catch(IllegalArgumentException ex){throw BusinessFailure.missing();}}
    private UUID context(UUID requested){Identity a=actor();UUID effective=a.scope()==Scope.TENANT?a.tenantId():requested;if(effective==null)throw BusinessFailure.invalid("Concrete Tenant required");requireTenant(effective);return effective;}
    private void selection(UUID tenant,AssetService.CatalogSelection s){if(s instanceof AssetService.ProductSelection p)catalog.scoped(CatalogService.Kind.PRODUCT,p.productId(),tenant,false);else if(s instanceof AssetService.DirectSelection d){catalog.scoped(CatalogService.Kind.CATEGORY,d.categoryId(),tenant,false);catalog.scoped(CatalogService.Kind.MANUFACTURER,d.manufacturerId(),tenant,false);}}
    private Identity actor(){Identity a=CurrentIdentity.require();Authorization.active(a);return a;}
    private UUID onlyTenant(){Identity a=actor();return a.scope()==Scope.TENANT?a.tenantId():null;}
    private Identity writer(){Identity a=actor();Authorization.administrator(a);return a;}
    private UUID assetTenant(UUID id){writer();UUID tenant=reads.tenantOf(id,onlyTenant());if(assets.get(tenant,id).deletedAt()!=null)throw BusinessFailure.missing();return tenant;}
    @Transactional(readOnly=true) public Page<AssetRead> list(UUID tenant,AssetQuery q){tenant=context(tenant);return reads.list(tenant,q);}
    @Transactional(readOnly=true) public Page<AssetRead> all(UUID tenant,AssetQuery q){Identity a=actor();if(a.scope()!=Scope.GLOBAL)throw AuthFailure.denied();if(tenant!=null)requireTenant(tenant);return reads.list(tenant,q);}
    @Transactional(readOnly=true) public AssetRead detail(UUID id){return reads.detail(id,onlyTenant());}
    @Transactional(readOnly=true) public Page<AssetRead.Event> events(UUID id,AssetHistoryQuery q){return reads.events(id,onlyTenant(),q);}
    @Transactional public AssetService.View register(UUID tenant,AssetService.Registration command){writer();tenant=context(tenant);selection(tenant,command.catalog());return assets.register(tenant,command,actor().actor());}
    @Transactional public AssetService.View text(UUID id,long version,String serial,String inventory,String note,String localIdentifier){return assets.editText(assetTenant(id),id,version,serial,inventory,note,localIdentifier,actor().actor());}
    @Transactional public AssetService.View catalog(UUID id,long version,AssetService.CatalogSelection selection){UUID tenant=assetTenant(id);selection(tenant,selection);return assets.changeCatalog(tenant,id,version,selection,actor().actor());}
    @Transactional public AssetService.View loanability(UUID id,long version,boolean loanable){return assets.changeLoanability(assetTenant(id),id,version,loanable,actor().actor());}
    @Transactional public AssetService.View lifecycle(UUID id,long version,LifecycleState target,DeregistrationReason reason,String note){return assets.lifecycle(assetTenant(id),id,version,target,reason,note,actor().actor());}
    @Transactional public AssetService.View condition(UUID id,long version,ConditionState target,String note){return assets.condition(assetTenant(id),id,version,target,note,actor().actor());}
    @Transactional public AssetService.View location(UUID id,long version,UUID target){UUID tenant=assetTenant(id);places.readScoped(PlaceService.Kind.LOCATION,target,tenant);return assets.changeLocation(tenant,id,version,target,actor().actor());}
    @Transactional public AssetService.View owner(UUID id,long version,UUID target){UUID tenant=assetTenant(id);if(target!=null)places.readScoped(PlaceService.Kind.OWNER,target,tenant);return assets.changeOwner(tenant,id,version,target,actor().actor());}
    @Transactional public AssetService.View loan(UUID id,long version,String borrower,Instant returnAt,String note){return assets.loan(assetTenant(id),id,version,borrower,returnAt,note,actor().actor());}
    @Transactional public AssetService.View returned(UUID id,long version){return assets.returned(assetTenant(id),id,version,actor().actor());}
    private Identity auditActor(){Identity a=actor();Authorization.administrator(a);return a;}
    @Transactional(readOnly=true) public Page<AuditRecord> audit(AuditQuery q){Identity a=auditActor();if(!a.globalAdmin() && (q.tenantId()!=null || q.globalOnly()!=null))throw AuthFailure.denied();return audits.list(a.tenantId(),a.globalAdmin(),q);}
    @Transactional(readOnly=true) public AuditRecord audit(UUID id){Identity a=auditActor();return audits.detail(id,a.tenantId(),a.globalAdmin());}
}
