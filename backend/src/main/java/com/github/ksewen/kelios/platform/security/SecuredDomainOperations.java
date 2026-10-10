package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.catalog.CatalogService;
import com.github.ksewen.kelios.identity.auth.*;
import com.github.ksewen.kelios.inventory.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Secured application boundary around accepted internal Phase 1 services; no inventory HTTP/UI. */
@Service
public class SecuredDomainOperations {
    private final AssetService assets;private final PlaceService places;private final CatalogService catalog;private final CatalogMergeService merges;
    public SecuredDomainOperations(AssetService assets,PlaceService places,CatalogService catalog,CatalogMergeService merges){this.assets=assets;this.places=places;this.catalog=catalog;this.merges=merges;}
    private Identity actor(){return CurrentIdentity.require();}
    private UUID assetTenant(UUID id,boolean write){var actor=actor();Authorization.active(actor);UUID tenant=assets.tenantOf(id);if(write)Authorization.writeTenant(actor,tenant);else Authorization.readTenant(actor,tenant);return tenant;}
    @Transactional(readOnly=true) public AssetService.View asset(UUID id){return assets.get(assetTenant(id,false),id);}
    @Transactional public AssetService.View register(UUID tenant,AssetService.Registration command){var actor=actor();Authorization.writeTenant(actor,tenant);return assets.register(tenant,command,actor.actor());}
    @Transactional public AssetService.View location(UUID asset,long version,UUID location){return assets.changeLocation(assetTenant(asset,true),asset,version,location,actor().actor());}
    @Transactional public AssetService.View owner(UUID asset,long version,UUID owner){return assets.changeOwner(assetTenant(asset,true),asset,version,owner,actor().actor());}
    @Transactional public AssetService.View assetCatalog(UUID asset,long version,AssetService.CatalogSelection selection){return assets.changeCatalog(assetTenant(asset,true),asset,version,selection,actor().actor());}
    @Transactional public AssetService.View assetText(UUID asset,long version,String serial,String inventory,String note){return assetText(asset,version,serial,inventory,note,null);}
    @Transactional public AssetService.View assetText(UUID asset,long version,String serial,String inventory,String note,String localIdentifier){return assets.editText(assetTenant(asset,true),asset,version,serial,inventory,note,localIdentifier,actor().actor());}
    @Transactional public AssetService.View deleteAsset(UUID asset,long version){return assets.delete(assetTenant(asset,true),asset,version,actor().actor());}
    @Transactional(readOnly=true) public PlaceService.Place place(PlaceService.Kind kind,UUID id){var actor=actor();Authorization.active(actor);var place=places.read(kind,id);Authorization.readTenant(actor,place.tenantId());return place;}
    @Transactional public UUID createPlace(PlaceService.Kind kind,UUID tenant,String name){var actor=actor();Authorization.writeTenant(actor,tenant);return places.create(kind,tenant,name,actor.actor());}
    @Transactional public void editPlace(PlaceService.Kind kind,UUID id,String name){var actor=actor();Authorization.active(actor);var place=places.read(kind,id);Authorization.writeTenant(actor,place.tenantId());places.edit(kind,place.tenantId(),id,name,actor.actor());}
    @Transactional public void deletePlace(PlaceService.Kind kind,UUID id){var actor=actor();Authorization.active(actor);var place=places.read(kind,id);Authorization.writeTenant(actor,place.tenantId());places.delete(kind,place.tenantId(),id,actor.actor());}
    @Transactional(readOnly=true) public CatalogService.Entry catalog(CatalogService.Kind kind,UUID id){
        var actor=actor();Authorization.active(actor);var entry=catalog.read(kind,id);
        if(!entry.global())Authorization.readTenant(actor,entry.tenantId());return entry;
    }
    @Transactional public UUID category(UUID tenant,String name){var actor=actor();Authorization.writeCatalog(actor,tenant);return catalog.createCategory(tenant,name,actor.actor());}
    @Transactional public UUID manufacturer(UUID tenant,String name){var actor=actor();Authorization.writeCatalog(actor,tenant);return catalog.createManufacturer(tenant,name,actor.actor());}
    @Transactional public UUID product(UUID tenant,UUID category,UUID manufacturer,String name,String number){var actor=actor();Authorization.writeCatalog(actor,tenant);return catalog.createProduct(tenant,category,manufacturer,name,number,actor.actor());}
    @Transactional public void editLabel(CatalogService.Kind kind,UUID id,String name){var actor=actor();Authorization.active(actor);var entry=catalog.forUpdate(kind,id);Authorization.writeCatalog(actor,entry.tenantId());catalog.editLabel(kind,id,name,actor.actor());}
    @Transactional public void editProduct(UUID id,UUID category,UUID manufacturer,String name,String number){var actor=actor();Authorization.active(actor);var entry=catalog.forUpdate(CatalogService.Kind.PRODUCT,id);Authorization.writeCatalog(actor,entry.tenantId());catalog.editProduct(id,category,manufacturer,name,number,actor.actor());}
    @Transactional public void deleteCatalog(CatalogService.Kind kind,UUID id){var actor=actor();Authorization.active(actor);var entry=catalog.forUpdate(kind,id);Authorization.writeCatalog(actor,entry.tenantId());catalog.delete(kind,id,actor.actor());}
    @Transactional public void publish(CatalogService.Kind kind,UUID id){var actor=actor();Authorization.globalAdmin(actor);catalog.publish(kind,id,actor.actor());}
    @Transactional public void merge(CatalogService.MergeKind kind,UUID source,UUID target){var actor=actor();Authorization.globalAdmin(actor);merges.merge(kind,source,target,actor.actor());}
}
