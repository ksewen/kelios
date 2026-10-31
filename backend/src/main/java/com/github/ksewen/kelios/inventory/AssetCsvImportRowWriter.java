package com.github.ksewen.kelios.inventory;

import com.github.ksewen.kelios.audit.Actor;
import com.github.ksewen.kelios.audit.AuditService;
import com.github.ksewen.kelios.catalog.CatalogService;
import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.tenancy.TenantService;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Isolated legacy creation boundary; each invocation commits one complete Asset and its history. */
@Service
public class AssetCsvImportRowWriter {
    public record Input(UUID tenantId,long sourceRow,Map<String,Object> values,Map<String,UUID> references) {}
    public record Created(UUID id,String tag) {}
    private final EntityManager em;
    private final TenantService tenants;
    private final CatalogService catalog;
    private final PlaceService places;
    private final AssetTagAllocator tags;
    private final AssetService assets;
    private final AssetHistory history;
    private final AuditService audit;
    private final Clock clock;
    public AssetCsvImportRowWriter(EntityManager em,TenantService tenants,CatalogService catalog,PlaceService places,
        AssetTagAllocator tags,AssetService assets,AssetHistory history,AuditService audit,Clock clock){
        this.em=em;this.tenants=tenants;this.catalog=catalog;this.places=places;this.tags=tags;
        this.assets=assets;this.history=history;this.audit=audit;this.clock=clock;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Created write(Input input,Actor actor){
        UUID tenant=input.tenantId();
        tenants.require(tenant);
        var refs=input.references();
        UUID categoryId=refs.get("categoryId"),manufacturerId=refs.get("manufacturerId"),productId=refs.get("productId");
        UUID locationId=refs.get("locationId"),ownerId=refs.get("ownerId");
        if(categoryId==null || manufacturerId==null || locationId==null)
            throw BusinessFailure.invalid("Required import reference missing");
        if(productId!=null){
            var initial=catalog.read(CatalogService.Kind.PRODUCT,productId);
            var category=catalog.share(CatalogService.Kind.CATEGORY,categoryId);
            var manufacturer=catalog.share(CatalogService.Kind.MANUFACTURER,manufacturerId);
            var product=catalog.share(CatalogService.Kind.PRODUCT,productId);
            if(!Objects.equals(initial.categoryId(),product.categoryId()) ||
                !Objects.equals(initial.manufacturerId(),product.manufacturerId()) ||
                !categoryId.equals(product.categoryId()) || !manufacturerId.equals(product.manufacturerId()))
                throw new ObjectOptimisticLockingFailureException(AssetCsvImportRowWriter.class,productId);
            catalog.requireVisible(product,tenant,true);
            catalog.requireVisible(category,tenant,true);
            catalog.requireVisible(manufacturer,tenant,true);
        }else{
            catalog.requireVisible(catalog.share(CatalogService.Kind.CATEGORY,categoryId),tenant,true);
            catalog.requireVisible(catalog.share(CatalogService.Kind.MANUFACTURER,manufacturerId),tenant,true);
        }
        places.share(PlaceService.Kind.LOCATION,tenant,locationId,true);
        if(ownerId!=null)places.share(PlaceService.Kind.OWNER,tenant,ownerId,true);
        var values=input.values();
        LocalDate tagDate=LocalDate.parse((String)values.get("tagDate"));
        if(tagDate.isAfter(LocalDate.now(clock.withZone(ZoneOffset.UTC))))throw BusinessFailure.invalid("Future Tag date");
        String local=(String)values.get("localIdentifier");
        if(local!=null && (local.codePointCount(0,local.length())>128 ||
            local.codePoints().anyMatch(c->Character.getType(c)==Character.CONTROL || c==0x2028 || c==0x2029)))
            throw BusinessFailure.invalid("Invalid local identifier");
        Instant now=Instant.now(clock);
        Asset asset=new Asset();asset.id=UUID.randomUUID();asset.tenantId=tenant;
        asset.assetTag=tags.allocate(tagDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        asset.categoryId=categoryId;asset.manufacturerId=manufacturerId;asset.productId=productId;
        asset.locationId=locationId;asset.ownerId=ownerId;
        asset.lifecycle=LifecycleState.valueOf((String)values.get("lifecycle"));
        asset.condition=ConditionState.valueOf((String)values.get("condition"));
        asset.loanable=(Boolean)values.get("loanable");
        asset.localIdentifier=local;asset.serialNumber=(String)values.get("serialNumber");
        asset.externalInventoryNumber=(String)values.get("externalInventoryNumber");asset.note=(String)values.get("note");
        asset.createdAt=now;asset.updatedAt=now;asset.createdBy=actor.id();
        asset.createdByDisplayName=actor.displayName();asset.updatedBy=actor.id();
        em.persist(asset);em.flush();
        history.append(asset,"REGISTERED",actor,null,assets.snapshot(asset),Map.of("origin","LEGACY_CSV"),null);
        audit.record(actor,tenant,"ASSET_LEGACY_IMPORTED","ASSET",asset.id,
            Map.of("origin","LEGACY_CSV","sourceRow",input.sourceRow(),"assetTag",asset.assetTag));
        return new Created(asset.id,asset.assetTag);
    }
}
