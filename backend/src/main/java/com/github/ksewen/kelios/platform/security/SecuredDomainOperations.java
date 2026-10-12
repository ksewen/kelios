package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.catalog.CatalogService;
import com.github.ksewen.kelios.catalog.ProductModel;
import com.github.ksewen.kelios.catalog.ProductOperations;
import com.github.ksewen.kelios.catalog.CatalogLabel;
import com.github.ksewen.kelios.catalog.CatalogLabelOperations;
import com.github.ksewen.kelios.identity.UserService.Scope;
import com.github.ksewen.kelios.identity.auth.*;
import com.github.ksewen.kelios.inventory.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Secured application boundary around accepted internal Phase 1 services; no inventory HTTP/UI. */
@Service
public class SecuredDomainOperations {
    private final InventoryReads assetReads;private final AssetService assets;private final PlaceService places;private final CatalogService catalog;private final CatalogLabelOperations labels;private final ProductOperations products;private final PlaceOperations placeOperations;
    public SecuredDomainOperations(InventoryReads assetReads,AssetService assets,PlaceService places,CatalogService catalog,CatalogLabelOperations labels,ProductOperations products,PlaceOperations placeOperations){this.assetReads=assetReads;this.assets=assets;this.places=places;this.catalog=catalog;this.labels=labels;this.products=products;this.placeOperations=placeOperations;}
    private Identity actor(){return CurrentIdentity.require();}
    private UUID assetTenant(UUID id,boolean write){var actor=actor();Authorization.active(actor);UUID tenant=assetReads.tenantOf(id,actor.scope()==Scope.TENANT?actor.tenantId():null);if(write)Authorization.writeTenant(actor,tenant);else Authorization.readTenant(actor,tenant);return tenant;}
    @Transactional(readOnly=true) public AssetService.View asset(UUID id){return assets.get(assetTenant(id,false),id);}
    @Transactional public AssetService.View register(UUID tenant,AssetService.Registration command){var actor=actor();Authorization.active(actor);Authorization.administrator(actor);UUID effective=actor.scope()==Scope.TENANT?actor.tenantId():tenant;if(effective==null)throw com.github.ksewen.kelios.platform.BusinessFailure.invalid("Concrete Tenant required");return assets.register(effective,command,actor.actor());}
    @Transactional public AssetService.View location(UUID asset,long version,UUID location){return assets.changeLocation(assetTenant(asset,true),asset,version,location,actor().actor());}
    @Transactional public AssetService.View owner(UUID asset,long version,UUID owner){return assets.changeOwner(assetTenant(asset,true),asset,version,owner,actor().actor());}
    @Transactional public AssetService.View assetCatalog(UUID asset,long version,AssetService.CatalogSelection selection){return assets.changeCatalog(assetTenant(asset,true),asset,version,selection,actor().actor());}
    @Transactional public AssetService.View assetText(UUID asset,long version,String serial,String inventory,String note){return assetText(asset,version,serial,inventory,note,null);}
    @Transactional public AssetService.View assetText(UUID asset,long version,String serial,String inventory,String note,String localIdentifier){return assets.editText(assetTenant(asset,true),asset,version,serial,inventory,note,localIdentifier,actor().actor());}
    @Transactional public AssetService.View deleteAsset(UUID asset,long version){return assets.delete(assetTenant(asset,true),asset,version,actor().actor());}
    @Transactional(readOnly=true) public PlaceService.Place place(PlaceService.Kind kind,UUID id){var actor=actor();Authorization.active(actor);return places.readScoped(kind,id,actor.scope()==Scope.TENANT?actor.tenantId():null);}
    @Transactional public UUID createPlace(PlaceService.Kind kind,UUID tenant,String name){return createPlace(kind,tenant,name,null);}
    @Transactional public UUID createPlace(PlaceService.Kind kind,UUID tenant,String name,String note){return placeOperations.create(kind,tenant,new PlaceModel.Command(name,note));}
    @Transactional public void editPlace(PlaceService.Kind kind,UUID id,String name){editPlace(kind,id,name,null);}
    @Transactional public void editPlace(PlaceService.Kind kind,UUID id,String name,String note){placeOperations.edit(kind,id,new PlaceModel.Command(name,note));}
    @Transactional public void deletePlace(PlaceService.Kind kind,UUID id){placeOperations.delete(kind,id);}
    @Transactional(readOnly=true) public CatalogService.Entry catalog(CatalogService.Kind kind,UUID id){
        var actor=actor();Authorization.active(actor);var entry=catalog.scoped(kind,id,actor.scope()==Scope.TENANT?actor.tenantId():null,false);
        if(!entry.global())Authorization.readTenant(actor,entry.tenantId());return entry;
    }
    @Transactional public UUID category(UUID tenant,String name){return labels.create(CatalogLabel.Kind.CATEGORY,tenant,new CatalogLabel.Name(name));}
    @Transactional public UUID manufacturer(UUID tenant,String name){return labels.create(CatalogLabel.Kind.MANUFACTURER,tenant,new CatalogLabel.Name(name));}
    @Transactional public UUID product(UUID tenant,UUID category,UUID manufacturer,String name,String number){return products.create(tenant,new ProductModel.Command(name,category,manufacturer,number));}
    @Transactional public void editLabel(CatalogService.Kind kind,UUID id,String name){labels.edit(CatalogLabel.Kind.valueOf(kind.name()),id,new CatalogLabel.Name(name));}
    @Transactional public void editProduct(UUID id,UUID category,UUID manufacturer,String name,String number){products.edit(id,new ProductModel.Command(name,category,manufacturer,number));}
    @Transactional public void deleteCatalog(CatalogService.Kind kind,UUID id){if(kind==CatalogService.Kind.PRODUCT)products.delete(id);else labels.delete(CatalogLabel.Kind.valueOf(kind.name()),id);}
    @Transactional public void publish(CatalogService.Kind kind,UUID id){if(kind==CatalogService.Kind.PRODUCT)products.publish(id);else labels.publish(CatalogLabel.Kind.valueOf(kind.name()),id);}
    @Transactional public void merge(CatalogService.MergeKind kind,UUID source,UUID target){labels.merge(CatalogLabel.Kind.valueOf(kind.name()),source,target);}
}
