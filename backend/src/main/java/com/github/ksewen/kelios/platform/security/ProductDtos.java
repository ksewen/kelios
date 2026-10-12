package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.audit.SystemActor;
import com.github.ksewen.kelios.catalog.ProductModel;
import com.github.ksewen.kelios.catalog.ProductQuery;
import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.platform.ListInput;
import com.github.ksewen.kelios.platform.Page;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Product endpoint DTOs and explicit mappings; no persistence entity exposure or overposting. */
final class ProductDtos {
    private ProductDtos() {}
    record Created(UUID id) {}
    record Command(String name,UUID categoryId,UUID manufacturerId,String manufacturerNumber) {
        @com.fasterxml.jackson.annotation.JsonCreator
        public static Command bind(@com.fasterxml.jackson.annotation.JsonProperty("name") Object name,
                @com.fasterxml.jackson.annotation.JsonProperty("categoryId") Object category,
                @com.fasterxml.jackson.annotation.JsonProperty("manufacturerId") Object manufacturer,
                @com.fasterxml.jackson.annotation.JsonProperty("manufacturerNumber") Object number) {
            if(!(name instanceof String text))throw BusinessFailure.invalid("Name text required");
            if(number!=null && !(number instanceof String))throw BusinessFailure.invalid("Manufacturer number text required");
            return new Command(text,reference(category),reference(manufacturer),(String)number);
        }
        private static UUID reference(Object value){if(!(value instanceof String s))throw BusinessFailure.invalid("UUID text required");var id=ListInput.uuid(s);if(id==null)throw BusinessFailure.invalid("Reference required");return id;}
        @com.fasterxml.jackson.annotation.JsonAnySetter public void reject(String key,Object value){throw BusinessFailure.invalid("Unknown command field");}
        ProductModel.Command model(){return new ProductModel.Command(name,categoryId,manufacturerId,manufacturerNumber);}
    }
    static ProductQuery query(Map<String,String> input){
        ListInput.keys(input,"visibility","categoryId","manufacturerId","manufacturerNumber","sort","direction");
        int page=ListInput.page(Map.of("page",input.getOrDefault("page","0"))),size=ListInput.size(Map.of("size",input.getOrDefault("size","20")));
        ProductModel.Visibility visibility=null;
        if(input.containsKey("visibility"))try{visibility=ProductModel.Visibility.valueOf(input.get("visibility"));}catch(IllegalArgumentException ex){throw BusinessFailure.invalid("Invalid visibility");}
        ProductQuery.Sort sort=input.containsKey("sort")?switch(input.get("sort")){case "name"->ProductQuery.Sort.NAME;case "visibility"->ProductQuery.Sort.VISIBILITY;case "manufacturerNumber"->ProductQuery.Sort.MANUFACTURER_NUMBER;default->throw BusinessFailure.invalid("Unsupported Product sort");}:null;
        ProductQuery.Direction direction=input.containsKey("direction")?switch(input.get("direction")){case "asc"->ProductQuery.Direction.ASC;case "desc"->ProductQuery.Direction.DESC;default->throw BusinessFailure.invalid("Invalid direction");}:null;
        return new ProductQuery(page,size,input.get("q"),visibility,ListInput.uuid(input.get("categoryId")),ListInput.uuid(input.get("manufacturerId")),input.get("manufacturerNumber"),sort,direction);
    }
    record Reference(UUID id,String name) {}
    record Metadata(UUID createdBy,Instant createdAt,UUID updatedBy,Instant updatedAt,String createdByDisplayName,String updatedByDisplayName) {}
    record Item(UUID id,String name,String manufacturerNumber,String visibility,UUID tenantId,Reference tenant,UUID categoryId,Reference category,UUID manufacturerId,Reference manufacturer,Metadata metadata) {}
    record Detail(UUID id,String name,String manufacturerNumber,String visibility,UUID tenantId,Reference tenant,UUID categoryId,Reference category,UUID manufacturerId,Reference manufacturer,boolean deleted,Instant deletedAt,UUID deletedBy,Metadata metadata) {}
    private static Reference ref(ProductModel.Reference r){return r==null?null:new Reference(r.id(),r.name());}
    private static UUID tenantId(ProductModel p){return p.tenant()==null?null:p.tenant().id();}
    private static Metadata metadata(ProductModel p){var m=p.metadata();return new Metadata(m.createdBy(),m.createdAt(),m.updatedBy(),m.updatedAt(),SystemActor.displayName(m.createdBy()),SystemActor.displayName(m.updatedBy()));}
    static Page<Item> page(Page<ProductModel> page){return new Page<>(page.items().stream().map(p->new Item(p.id(),p.name(),p.manufacturerNumber(),p.visibility().name(),tenantId(p),ref(p.tenant()),p.category().id(),ref(p.category()),p.manufacturer().id(),ref(p.manufacturer()),metadata(p))).toList(),page.page(),page.size(),page.totalElements());}
    static Detail detail(ProductModel p){return new Detail(p.id(),p.name(),p.manufacturerNumber(),p.visibility().name(),tenantId(p),ref(p.tenant()),p.category().id(),ref(p.category()),p.manufacturer().id(),ref(p.manufacturer()),p.deletedAt()!=null,p.deletedAt(),p.deletedBy(),metadata(p));}
}
