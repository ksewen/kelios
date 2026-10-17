package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.audit.SystemActor;
import com.github.ksewen.kelios.inventory.PlaceModel;
import com.github.ksewen.kelios.inventory.PlaceQuery;
import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.platform.ListInput;
import com.github.ksewen.kelios.platform.Page;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Endpoint-owned Place transport projections and explicit mappings; never persisted. */
final class PlaceDtos {
    private PlaceDtos(){}
    record Created(UUID id){}
    record Command(String name,String note){
        @com.fasterxml.jackson.annotation.JsonCreator public static Command bind(@com.fasterxml.jackson.annotation.JsonProperty("name") Object name,@com.fasterxml.jackson.annotation.JsonProperty("note") Object note){
            if(!(name instanceof String text))throw BusinessFailure.invalid("Name text required");
            if(note!=null && !(note instanceof String))throw BusinessFailure.invalid("Note text required");return new Command(text,(String)note);
        }
        @com.fasterxml.jackson.annotation.JsonAnySetter public void reject(String key,Object value){throw BusinessFailure.invalid("Unknown command field");}
        PlaceModel.Command model(){return new PlaceModel.Command(name,note);}
    }
    static PlaceQuery query(Map<String,String> input){
        ListInput.keys(input,"sort","direction");int page=ListInput.page(Map.of("page",input.getOrDefault("page","0"))),size=ListInput.size(Map.of("size",input.getOrDefault("size","20")));
        PlaceQuery.Sort sort=input.containsKey("sort")?switch(input.get("sort")){case "name"->PlaceQuery.Sort.NAME;default->throw BusinessFailure.invalid("Unsupported Place sort");}:null;
        PlaceQuery.Direction direction=input.containsKey("direction")?switch(input.get("direction")){case "asc"->PlaceQuery.Direction.ASC;case "desc"->PlaceQuery.Direction.DESC;default->throw BusinessFailure.invalid("Invalid direction");}:null;
        return new PlaceQuery(page,size,input.get("q"),sort,direction);
    }
    record Tenant(UUID id,String name){}
    record Metadata(UUID createdBy,Instant createdAt,UUID updatedBy,Instant updatedAt,String createdByDisplayName,String updatedByDisplayName){}
    record Item(UUID id,UUID tenantId,Tenant tenant,String name,Metadata metadata){}
    record Detail(UUID id,UUID tenantId,Tenant tenant,String name,String note,boolean deleted,Instant deletedAt,UUID deletedBy,Metadata metadata){}
    private static Tenant tenant(PlaceModel p){return new Tenant(p.tenant().id(),p.tenant().name());}
    private static Metadata metadata(PlaceModel p){var m=p.metadata();return new Metadata(m.createdBy(),m.createdAt(),m.updatedBy(),m.updatedAt(),
        m.createdByDisplayName()==null?SystemActor.displayName(m.createdBy()):m.createdByDisplayName(),
        m.updatedByDisplayName()==null?SystemActor.displayName(m.updatedBy()):m.updatedByDisplayName());}
    static Page<Item> page(Page<PlaceModel> page){return new Page<>(page.items().stream().map(p->new Item(p.id(),p.tenant().id(),tenant(p),p.name(),metadata(p))).toList(),page.page(),page.size(),page.totalElements());}
    static Detail detail(PlaceModel p){return new Detail(p.id(),p.tenant().id(),tenant(p),p.name(),p.note(),p.deletedAt()!=null,p.deletedAt(),p.deletedBy(),metadata(p));}
}
