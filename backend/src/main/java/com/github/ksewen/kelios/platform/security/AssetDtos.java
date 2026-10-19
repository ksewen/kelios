package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.inventory.*;
import com.github.ksewen.kelios.platform.*;
import com.github.ksewen.kelios.audit.SystemActor;
import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonCreator;

/** Explicit Asset HTTP contracts. Commands bind only their owned inputs; writers never see transport DTOs. */
final class AssetDtos {
    private AssetDtos() {}
    static AssetQuery query(Map<String,String> q){
        ListInput.keys(q,"sort","direction","lifecycle","condition","locationId","ownerId","categoryId","manufacturerId","productId","loaned","loanable","localIdentifier","serialNumber","externalInventoryNumber");
        var sort=switch(q.getOrDefault("sort","createdAt")){case "tag"->AssetQuery.Sort.ASSET_TAG;case "createdAt"->AssetQuery.Sort.CREATED_AT;case "lifecycle"->AssetQuery.Sort.LIFECYCLE;case "condition"->AssetQuery.Sort.CONDITION;default->throw BusinessFailure.invalid("Unsupported Asset sort");};
        var direction=switch(q.getOrDefault("direction","desc")){case "asc"->AssetQuery.Direction.ASC;case "desc"->AssetQuery.Direction.DESC;default->throw BusinessFailure.invalid("Invalid direction");};
        return new AssetQuery(page(q),size(q),q.get("q"),state(q,"lifecycle",LifecycleState.class),state(q,"condition",ConditionState.class),
            ListInput.uuid(q.get("locationId")),ListInput.uuid(q.get("ownerId")),ListInput.uuid(q.get("categoryId")),ListInput.uuid(q.get("manufacturerId")),ListInput.uuid(q.get("productId")),
            bool(q,"loaned"),bool(q,"loanable"),q.get("localIdentifier"),q.get("serialNumber"),q.get("externalInventoryNumber"),sort,direction);
    }
    static AssetHistoryQuery history(Map<String,String> q){
        if(!Set.of("page","size").containsAll(q.keySet()))throw BusinessFailure.invalid("History supports pagination only");
        return new AssetHistoryQuery(page(q),size(q));
    }
    private static int page(Map<String,String> q){return ListInput.page(q);}
    private static int size(Map<String,String> q){return ListInput.size(Map.of("size",q.getOrDefault("size","20")));}
    private static Boolean bool(Map<String,String> q,String key){return q.containsKey(key)?ListInput.bool(q.get(key)):null;}
    private static <E extends Enum<E>> E state(Map<String,String> q,String key,Class<E> type){if(!q.containsKey(key))return null;try{return Enum.valueOf(type,q.get(key));}catch(IllegalArgumentException ex){throw BusinessFailure.invalid("Invalid "+key);}}
    record Reference(UUID id,String name) {}
    record State(UUID id,UUID tenantId,String assetTag,UUID categoryId,UUID manufacturerId,UUID productId,UUID locationId,UUID ownerId,LifecycleState lifecycle,ConditionState condition,boolean loanable,String serialNumber,String externalInventoryNumber,String note,String localIdentifier,long version,Instant deletedAt,String loanedTo,Instant loanedAt,Instant expectedReturnAt,String loanNote,Instant createdAt,UUID createdBy,Instant updatedAt,UUID updatedBy,String createdByDisplayName,String updatedByDisplayName) {}
    static State state(AssetService.View v){return new State(v.id(),v.tenantId(),v.assetTag(),v.categoryId(),v.manufacturerId(),v.productId(),v.locationId(),v.ownerId(),v.lifecycle(),v.condition(),v.loanable(),v.serialNumber(),v.externalInventoryNumber(),v.note(),v.localIdentifier(),v.version(),v.deletedAt(),v.loanedTo(),v.loanedAt(),v.expectedReturnAt(),v.loanNote(),v.createdAt(),v.createdBy(),v.updatedAt(),v.updatedBy(),v.createdByDisplayName()==null?SystemActor.displayName(v.createdBy()):v.createdByDisplayName(),SystemActor.displayName(v.updatedBy()));}
    record Item(UUID id,UUID tenantId,String assetTag,UUID categoryId,UUID manufacturerId,UUID productId,UUID locationId,UUID ownerId,LifecycleState lifecycle,ConditionState condition,boolean loanable,String serialNumber,String externalInventoryNumber,String localIdentifier,long version,String loanedTo,Instant loanedAt,Instant expectedReturnAt,Instant createdAt,UUID createdBy,Instant updatedAt,UUID updatedBy,Reference tenant,Reference category,Reference manufacturer,Reference product,Reference location,Reference owner,String createdByDisplayName,String updatedByDisplayName) {}
    record Detail(UUID id,UUID tenantId,String assetTag,UUID categoryId,UUID manufacturerId,UUID productId,UUID locationId,UUID ownerId,LifecycleState lifecycle,ConditionState condition,boolean loanable,String serialNumber,String externalInventoryNumber,String note,String localIdentifier,long version,Instant deletedAt,String loanedTo,Instant loanedAt,Instant expectedReturnAt,String loanNote,Instant createdAt,UUID createdBy,Instant updatedAt,UUID updatedBy,Reference tenant,Reference category,Reference manufacturer,Reference product,Reference location,Reference owner,String createdByDisplayName,String updatedByDisplayName) {}
    private static Reference ref(AssetRead.Reference r){return r==null?null:new Reference(r.id(),r.name());}
    static Detail detail(AssetRead r){var v=r.state();return new Detail(v.id(),v.tenantId(),v.assetTag(),v.categoryId(),v.manufacturerId(),v.productId(),v.locationId(),v.ownerId(),v.lifecycle(),v.condition(),v.loanable(),v.serialNumber(),v.externalInventoryNumber(),v.note(),v.localIdentifier(),v.version(),v.deletedAt(),v.loanedTo(),v.loanedAt(),v.expectedReturnAt(),v.loanNote(),v.createdAt(),v.createdBy(),v.updatedAt(),v.updatedBy(),ref(r.tenant()),ref(r.category()),ref(r.manufacturer()),ref(r.product()),ref(r.location()),ref(r.owner()),v.createdByDisplayName()==null?SystemActor.displayName(v.createdBy()):v.createdByDisplayName(),SystemActor.displayName(v.updatedBy()));}
    static Page<Item> page(Page<AssetRead> p){return new Page<>(p.items().stream().map(r->{var v=r.state();return new Item(v.id(),v.tenantId(),v.assetTag(),v.categoryId(),v.manufacturerId(),v.productId(),v.locationId(),v.ownerId(),v.lifecycle(),v.condition(),v.loanable(),v.serialNumber(),v.externalInventoryNumber(),v.localIdentifier(),v.version(),v.loanedTo(),v.loanedAt(),v.expectedReturnAt(),v.createdAt(),v.createdBy(),v.updatedAt(),v.updatedBy(),ref(r.tenant()),ref(r.category()),ref(r.manufacturer()),ref(r.product()),ref(r.location()),ref(r.owner()),v.createdByDisplayName()==null?SystemActor.displayName(v.createdBy()):v.createdByDisplayName(),SystemActor.displayName(v.updatedBy()));}).toList(),p.page(),p.size(),p.totalElements());}
    record Event(UUID id,UUID assetId,UUID tenantId,String type,Instant occurredAt,Instant createdAt,UUID actorId,String actorDisplayName,Map<String,Object> data,String note) {}
    static Page<Event> events(Page<AssetRead.Event> p){return new Page<>(p.items().stream().map(e->new Event(e.id(),e.assetId(),e.tenantId(),e.type(),e.occurredAt(),e.createdAt(),e.actorId(),e.actorDisplayName(),e.data(),e.note())).toList(),p.page(),p.size(),p.totalElements());}
    record Registration(UUID productId,UUID categoryId,UUID manufacturerId,UUID locationId,UUID ownerId,boolean loanable,String serialNumber,String externalInventoryNumber,String note,String localIdentifier) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static Registration bind(Map<String,Object> body){
            var c=new CommandInput(body,"productId","categoryId","manufacturerId","locationId","ownerId","loanable","serialNumber","externalInventoryNumber","note","localIdentifier");c.catalog();
            return new Registration(c.id("productId",false),c.id("categoryId",false),c.id("manufacturerId",false),c.id("locationId",true),c.id("ownerId",false),c.bool("loanable"),c.text("serialNumber"),c.text("externalInventoryNumber"),c.text("note"),c.text("localIdentifier"));
        }
        AssetService.Registration model(){return new AssetService.Registration(productId==null?new AssetService.DirectSelection(categoryId,manufacturerId):new AssetService.ProductSelection(productId),locationId,ownerId,loanable,serialNumber,externalInventoryNumber,note,localIdentifier);}
    }
    record Text(long version,String serialNumber,String externalInventoryNumber,String note,String localIdentifier) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static Text bind(Map<String,Object> body){var c=new CommandInput(body,"expectedVersion","serialNumber","externalInventoryNumber","note","localIdentifier");return new Text(c.version(),c.text("serialNumber"),c.text("externalInventoryNumber"),c.text("note"),c.text("localIdentifier"));}
    }
    record Catalog(long version,UUID productId,UUID categoryId,UUID manufacturerId) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static Catalog bind(Map<String,Object> body){var c=new CommandInput(body,"expectedVersion","productId","categoryId","manufacturerId");c.catalog();return new Catalog(c.version(),c.id("productId",false),c.id("categoryId",false),c.id("manufacturerId",false));}
        AssetService.CatalogSelection selection(){return productId==null?new AssetService.DirectSelection(categoryId,manufacturerId):new AssetService.ProductSelection(productId);}
    }
    record Loanability(long version,boolean loanable) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static Loanability bind(Map<String,Object> body){var c=new CommandInput(body,"expectedVersion","loanable");return new Loanability(c.version(),c.bool("loanable"));}
    }
    record Lifecycle(long version,LifecycleState target,DeregistrationReason reason,String note) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static Lifecycle bind(Map<String,Object> body){var c=new CommandInput(body,"expectedVersion","target","reason","note");return new Lifecycle(c.version(),c.state("target",LifecycleState.class,true),c.state("reason",DeregistrationReason.class,false),c.text("note"));}
    }
    record Condition(long version,ConditionState target,String note) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static Condition bind(Map<String,Object> body){var c=new CommandInput(body,"expectedVersion","target","note");return new Condition(c.version(),c.state("target",ConditionState.class,true),c.text("note"));}
    }
    record Location(long version,UUID locationId) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static Location bind(Map<String,Object> body){var c=new CommandInput(body,"expectedVersion","locationId");return new Location(c.version(),c.id("locationId",true));}
    }
    record Owner(long version,UUID ownerId) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static Owner bind(Map<String,Object> body){var c=new CommandInput(body,"expectedVersion","ownerId");c.present("ownerId");return new Owner(c.version(),c.id("ownerId",false));}
    }
    record Loan(long version,String borrower,Instant expectedReturnAt,String loanNote) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static Loan bind(Map<String,Object> body){var c=new CommandInput(body,"expectedVersion","borrower","expectedReturnAt","loanNote");return new Loan(c.version(),c.text("borrower"),c.instant("expectedReturnAt"),c.text("loanNote"));}
    }
    record Return(long version) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static Return bind(Map<String,Object> body){var c=new CommandInput(body,"expectedVersion");return new Return(c.version());}
    }
}
