package com.github.ksewen.kelios.inventory;

import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.audit.Actor;
import com.github.ksewen.kelios.catalog.CatalogService;
import com.github.ksewen.kelios.catalog.CatalogService.Kind;
import com.github.ksewen.kelios.tenancy.TenantService;
import jakarta.persistence.EntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {
    public sealed interface CatalogSelection permits ProductSelection, DirectSelection {}
    public record ProductSelection(UUID productId) implements CatalogSelection {
        public ProductSelection { Objects.requireNonNull(productId); }
    }
    public record DirectSelection(UUID categoryId,UUID manufacturerId) implements CatalogSelection {
        public DirectSelection {Objects.requireNonNull(categoryId);Objects.requireNonNull(manufacturerId);}
    }
    public record Registration(CatalogSelection catalog,UUID locationId,UUID ownerId,Boolean loanable,
        String serialNumber,String externalInventoryNumber,String note,String localIdentifier) {
        public Registration(CatalogSelection catalog,UUID locationId,UUID ownerId,Boolean loanable,String serialNumber,String externalInventoryNumber,String note) { this(catalog,locationId,ownerId,loanable,serialNumber,externalInventoryNumber,note,null); }
        public Registration {Objects.requireNonNull(catalog);Objects.requireNonNull(locationId);Objects.requireNonNull(loanable);}
    }
    public record View(UUID id,UUID tenantId,String assetTag,UUID categoryId,UUID manufacturerId,UUID productId,
        UUID locationId,UUID ownerId,LifecycleState lifecycle,ConditionState condition,boolean loanable,
        String serialNumber,String externalInventoryNumber,String note,String localIdentifier,long version,Instant deletedAt,
        String loanedTo,Instant loanedAt,Instant expectedReturnAt,String loanNote,Instant createdAt,UUID createdBy,Instant updatedAt,UUID updatedBy) {}
    private record References(UUID categoryId,UUID manufacturerId,UUID productId) {}
    private final EntityManager em;
    private final TenantService tenants;
    private final CatalogService catalog;
    private final PlaceService places;
    private final AssetTagAllocator tags;
    private final AssetHistory history;
    private final Clock clock;
    public AssetService(EntityManager em,TenantService tenants,CatalogService catalog,PlaceService places,AssetTagAllocator tags,AssetHistory history,Clock clock) {
        this.em=em;this.tenants=tenants;this.catalog=catalog;this.places=places;this.tags=tags;this.history=history;this.clock=clock;
    }
    private static String optional(String value) {return value==null || value.isBlank()?null:value.strip();}
    private static String localIdentifier(String value) {
        String normalized=optional(value);
        if(normalized==null)return null;
        if(normalized.codePointCount(0,normalized.length())>128 || normalized.codePoints().anyMatch(c -> Character.getType(c)==Character.CONTROL || c==0x2028 || c==0x2029))
            throw BusinessFailure.invalid("Local identifier must be at most 128 Unicode code points and contain no control or line separator characters");
        return normalized;
    }
    private References resolve(UUID tenant,CatalogSelection selection) {
        if(selection instanceof ProductSelection p) {
            var initial=catalog.read(Kind.PRODUCT,p.productId());
            var category=catalog.share(Kind.CATEGORY,initial.categoryId());var manufacturer=catalog.share(Kind.MANUFACTURER,initial.manufacturerId());
            var product=catalog.share(Kind.PRODUCT,p.productId());
            if(!initial.categoryId().equals(product.categoryId()) || !initial.manufacturerId().equals(product.manufacturerId()))
                throw new ObjectOptimisticLockingFailureException(ProductSelection.class,p.productId());
            catalog.requireVisible(product,tenant,true);catalog.requireVisible(category,tenant,true);catalog.requireVisible(manufacturer,tenant,true);
            return new References(product.categoryId(),product.manufacturerId(),product.id());
        }
        if(selection instanceof DirectSelection d) {
            catalog.requireVisible(catalog.share(Kind.CATEGORY,d.categoryId()),tenant,true);
            catalog.requireVisible(catalog.share(Kind.MANUFACTURER,d.manufacturerId()),tenant,true);
            return new References(d.categoryId(),d.manufacturerId(),null);
        }
        throw new BusinessFailure("INVALID_INPUT","Catalog selection required");
    }
    @Transactional public View register(UUID tenant,Registration command,Actor actor) {
        tenants.require(tenant);var refs=resolve(tenant,command.catalog());
        places.share(PlaceService.Kind.LOCATION,tenant,command.locationId(),true);
        if(command.ownerId()!=null)places.share(PlaceService.Kind.OWNER,tenant,command.ownerId(),true);
        Instant now=Instant.now(clock);Asset a=new Asset();a.id=UUID.randomUUID();a.tenantId=tenant;a.assetTag=tags.allocate(now);
        a.categoryId=refs.categoryId();a.manufacturerId=refs.manufacturerId();a.productId=refs.productId();a.locationId=command.locationId();a.ownerId=command.ownerId();
        a.loanable=command.loanable();a.lifecycle=LifecycleState.REGISTERED;a.condition=ConditionState.UNKNOWN;
        a.serialNumber=optional(command.serialNumber());a.externalInventoryNumber=optional(command.externalInventoryNumber());a.note=optional(command.note());a.localIdentifier=localIdentifier(command.localIdentifier());
        a.createdAt=now;a.updatedAt=now;a.createdBy=actor.id();a.updatedBy=actor.id();em.persist(a);em.flush();
        history.append(a,"REGISTERED",actor,null,snapshot(a));return view(a);
    }
    private Asset load(UUID tenant,UUID id) {
        Asset a=em.find(Asset.class,id);if(a==null || !Objects.equals(a.tenantId,tenant))throw new BusinessFailure("NOT_FOUND","Asset not found in Tenant");return a;
    }
    private Asset expected(UUID tenant,UUID id,long version) {
        Asset a=load(tenant,id);if(a.version!=version)throw new ObjectOptimisticLockingFailureException(Asset.class,id);return a;
    }
    @Transactional(readOnly=true) public UUID tenantOf(UUID id) {
        Asset asset=em.find(Asset.class,id);if(asset==null)throw new BusinessFailure("NOT_FOUND","Asset not found");return asset.tenantId;
    }
    @Transactional(readOnly=true) public View get(UUID tenant,UUID id) {return view(load(tenant,id));}
    private View view(Asset a) {return new View(a.id,a.tenantId,a.assetTag,a.categoryId,a.manufacturerId,a.productId,a.locationId,a.ownerId,a.lifecycle,a.condition,a.loanable,a.serialNumber,a.externalInventoryNumber,a.note,a.localIdentifier,a.version,a.deletedAt,a.loanedTo,a.loanedAt,a.expectedReturnAt,a.loanNote,a.createdAt,a.createdBy,a.updatedAt,a.updatedBy);}
    /** Snapshots load authoritative display data; also protect parents used by before/after history. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Map<String,Object> snapshot(Asset a) {
        Map<String,Object> data=new LinkedHashMap<>();data.put("id",a.id.toString());data.put("tenantId",a.tenantId.toString());data.put("assetTag",a.assetTag);
        data.put("productId",a.productId==null?null:a.productId.toString());data.put("categoryId",a.categoryId.toString());data.put("manufacturerId",a.manufacturerId.toString());
        data.put("category",catalog.share(Kind.CATEGORY,a.categoryId).snapshot());data.put("manufacturer",catalog.share(Kind.MANUFACTURER,a.manufacturerId).snapshot());
        data.put("product",a.productId==null?null:catalog.share(Kind.PRODUCT,a.productId).snapshot());
        data.put("locationId",a.locationId.toString());data.put("location",places.share(PlaceService.Kind.LOCATION,a.tenantId,a.locationId,false).snapshot());
        data.put("ownerId",a.ownerId==null?null:a.ownerId.toString());data.put("owner",a.ownerId==null?null:places.share(PlaceService.Kind.OWNER,a.tenantId,a.ownerId,false).snapshot());
        data.put("lifecycle",a.lifecycle.name());data.put("condition",a.condition.name());data.put("loanable",a.loanable);
        data.put("serialNumber",a.serialNumber);data.put("externalInventoryNumber",a.externalInventoryNumber);data.put("note",a.note);data.put("localIdentifier",a.localIdentifier);
        data.put("loanedTo",a.loanedTo);data.put("loanedAt",a.loanedAt==null?null:a.loanedAt.toString());
        data.put("expectedReturnAt",a.expectedReturnAt==null?null:a.expectedReturnAt.toString());data.put("loanNote",a.loanNote);
        data.put("deletedAt",a.deletedAt==null?null:a.deletedAt.toString());return data;
    }
    private View finish(Asset a,String event,Actor actor,Map<String,Object> before) {
        return finish(a,event,actor,before,Map.of(),null);
    }
    private View finish(Asset a,String event,Actor actor,Map<String,Object> before,Map<String,Object> context,String note) {
        Instant now=Instant.now(clock);
        // A successful explicit command still advances @Version when its values are unchanged.
        // Distinguish equal-clock timestamps at PostgreSQL microsecond precision.
        a.updatedAt=now.equals(a.updatedAt)?now.plusNanos(1000):now;a.updatedBy=actor.id();
        try { em.flush(); }
        catch (jakarta.persistence.OptimisticLockException ex) {
            throw new ObjectOptimisticLockingFailureException(Asset.class,a.id,ex);
        }
        history.append(a,event,actor,before,snapshot(a),context,optional(note));return view(a);
    }
    @Transactional public View changeLocation(UUID tenant,UUID id,long version,UUID locationId,Actor actor) {
        Asset a=expected(tenant,id,version);var before=snapshot(a);places.share(PlaceService.Kind.LOCATION,tenant,locationId,true);
        if(a.locationId.equals(locationId))throw new BusinessFailure("INVALID_INPUT","Location unchanged");a.locationId=locationId;return finish(a,"LOCATION_CHANGED",actor,before);
    }
    @Transactional public View changeOwner(UUID tenant,UUID id,long version,UUID ownerId,Actor actor) {
        Asset a=expected(tenant,id,version);var before=snapshot(a);if(ownerId!=null)places.share(PlaceService.Kind.OWNER,tenant,ownerId,true);
        if(Objects.equals(a.ownerId,ownerId))throw new BusinessFailure("INVALID_INPUT","Owner unchanged");a.ownerId=ownerId;return finish(a,"OWNER_CHANGED",actor,before);
    }
    @Transactional public View changeCatalog(UUID tenant,UUID id,long version,CatalogSelection selection,Actor actor) {
        Asset a=expected(tenant,id,version);var before=snapshot(a);var refs=resolve(tenant,selection);
        a.categoryId=refs.categoryId();a.manufacturerId=refs.manufacturerId();a.productId=refs.productId();return finish(a,"UPDATED",actor,before);
    }
    @Transactional public View editText(UUID tenant,UUID id,long version,String serialNumber,String inventoryNumber,String note,Actor actor) {
        return editText(tenant,id,version,serialNumber,inventoryNumber,note,null,actor);
    }
    @Transactional public View editText(UUID tenant,UUID id,long version,String serialNumber,String inventoryNumber,String note,String localIdentifier,Actor actor) {
        Asset a=expected(tenant,id,version);var before=snapshot(a);a.serialNumber=optional(serialNumber);a.externalInventoryNumber=optional(inventoryNumber);a.note=optional(note);a.localIdentifier=localIdentifier(localIdentifier);return finish(a,"UPDATED",actor,before);
    }
    @Transactional public View changeLoanability(UUID tenant,UUID id,long version,boolean loanable,Actor actor) {
        Asset a=expected(tenant,id,version);
        if(a.loanedTo!=null)throw new BusinessFailure("OUTSTANDING_LOAN","Return the current loan before changing loanability");
        var before=snapshot(a);a.loanable=loanable;return finish(a,"UPDATED",actor,before);
    }
    @Transactional public View lifecycle(UUID tenant,UUID id,long version,LifecycleState target,DeregistrationReason reason,String note,Actor actor) {
        Asset a=expected(tenant,id,version);
        if(target==null || (target==LifecycleState.DEREGISTERED)!=(reason!=null))throw BusinessFailure.invalid("Deregistration requires a structured reason; other transitions must omit it");
        if(!InventoryGraphs.LIFECYCLE.permits(a.lifecycle,target))throw new BusinessFailure("INVALID_TRANSITION","Lifecycle transition forbidden");
        var before=snapshot(a);a.lifecycle=InventoryGraphs.LIFECYCLE.transition(a.lifecycle,target);
        return finish(a,target==LifecycleState.DEREGISTERED?"DEREGISTERED":"LIFECYCLE_CHANGED",actor,before,reason==null?Map.of():Map.of("reason",reason.name()),note);
    }
    @Transactional public View condition(UUID tenant,UUID id,long version,ConditionState target,String note,Actor actor) {
        Asset a=expected(tenant,id,version);
        if(!InventoryGraphs.CONDITION.permits(a.condition,target))throw new BusinessFailure("INVALID_TRANSITION","Condition transition forbidden");
        var before=snapshot(a);a.condition=InventoryGraphs.CONDITION.transition(a.condition,target);return finish(a,"CONDITION_CHANGED",actor,before,Map.of(),note);
    }
    @Transactional public View loan(UUID tenant,UUID id,long version,String borrower,Instant expectedReturnAt,String loanNote,Actor actor) {
        Asset a=expected(tenant,id,version);
        if(borrower==null || borrower.isBlank())throw BusinessFailure.invalid("Borrower required");
        if(!a.loanable || a.lifecycle!=LifecycleState.DEPLOYED || a.condition!=ConditionState.WORKING || a.loanedTo!=null)
            throw new BusinessFailure("LOAN_GUARD","Asset must be loanable, DEPLOYED, WORKING and not already on loan");
        var before=snapshot(a);a.loanedTo=borrower.strip();a.loanedAt=Instant.now(clock);a.expectedReturnAt=expectedReturnAt;a.loanNote=optional(loanNote);
        return finish(a,"LOANED",actor,before);
    }
    @Transactional public View returned(UUID tenant,UUID id,long version,Actor actor) {
        Asset a=expected(tenant,id,version);
        if(a.loanedTo==null)throw new BusinessFailure("NO_CURRENT_LOAN","No outstanding loan to return");
        var before=snapshot(a);a.loanedTo=null;a.loanedAt=null;a.expectedReturnAt=null;a.loanNote=null;return finish(a,"RETURNED",actor,before);
    }
    @Transactional public View delete(UUID tenant,UUID id,long version,Actor actor) {
        Asset a=expected(tenant,id,version);if(a.deletedAt!=null)throw new BusinessFailure("DELETED_TARGET","Already deleted");var before=snapshot(a);
        a.deletedAt=Instant.now(clock);a.deletedBy=actor.id();return finish(a,"UPDATED",actor,before);
    }
}
