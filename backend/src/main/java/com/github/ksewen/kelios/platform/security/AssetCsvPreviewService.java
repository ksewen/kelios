package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.auth.Authorization;
import com.github.ksewen.kelios.inventory.ConditionState;
import com.github.ksewen.kelios.inventory.LifecycleState;
import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.tenancy.TenantService;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Read-only, unreserved import Preview; execution must revalidate every row. */
@Service
public class AssetCsvPreviewService {
    private static final Set<String> TARGETS=Set.of("localIdentifier","serialNumber","externalInventoryNumber","note",
        "productId","productName","categoryId","categoryName","manufacturerId","manufacturerName",
        "locationId","locationName","ownerId","ownerName","lifecycle","condition","loanable","tagDate");
    private static final List<String> REFERENCES=List.of("product","category","manufacturer","location","owner");
    public record Input(UUID tenantId,String sha256,String encoding,String delimiter,Map<String,String> columns,
        Map<String,Map<String,String>> values,Map<String,Map<String,UUID>> choices) {}
    public record Row(long rowNumber,String status,Map<String,String> source,Map<String,Object> normalized,
        Map<String,UUID> references,List<String> issues) {}
    public record Preview(String sha256,String encoding,String delimiter,List<String> headers,long totalRows,
        long ready,long warnings,long errors,List<Row> rows,
        Map<String,Map<String,AssetCsvReferenceResolver.Result>> referenceResolution) {}
    private final AssetCsvParser parser;
    private final AssetCsvReferenceResolver resolver;
    private final TenantService tenants;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public AssetCsvPreviewService(AssetCsvParser parser,AssetCsvReferenceResolver resolver,TenantService tenants,JdbcTemplate jdbc,Clock clock){
        this.parser=parser;this.resolver=resolver;this.tenants=tenants;this.jdbc=jdbc;this.clock=clock;
    }
    public AssetCsvParser.ParsePreview parsePreview(MultipartFile file,String encoding,String delimiter){
        Authorization.globalAdmin(CurrentIdentity.require());
        return parser.parsePreview(file,encoding,delimiter);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Preview preview(MultipartFile file,Input input){
        Authorization.globalAdmin(CurrentIdentity.require());
        if(input==null || input.tenantId()==null)throw BusinessFailure.invalid("Concrete Tenant required");
        tenants.require(input.tenantId());
        var parsed=parser.parse(file,input.encoding(),input.delimiter());
        if(input.sha256()==null || !parsed.sha256().equalsIgnoreCase(input.sha256()))throw BusinessFailure.invalid("CSV file differs from parsed preview");
        var columns=validate(input,parsed.headers());
        var valueMaps=input.values()==null?Map.<String,Map<String,String>>of():input.values();
        var choices=input.choices()==null?Map.<String,Map<String,UUID>>of():input.choices();
        var resolved=new LinkedHashMap<String,Map<String,AssetCsvReferenceResolver.Result>>();
        var rows=new ArrayList<Row>();
        var previous=new HashSet<List<String>>();
        for(var source:parsed.rows()){
            var issues=new ArrayList<String>();
            var normalized=new LinkedHashMap<String,Object>();
            var references=new LinkedHashMap<String,UUID>();
            boolean error=false;
            for(String text:List.of("localIdentifier","serialNumber","externalInventoryNumber","note")){
                String value=value(source.values(),columns,text);
                normalized.put(text,value==null || value.isBlank()?null:value.strip());
            }
            String local=(String)normalized.get("localIdentifier");
            if(local!=null){
                if(local.codePointCount(0,local.length())>128 || local.codePoints().anyMatch(c->Character.getType(c)==Character.CONTROL || c==0x2028 || c==0x2029)){
                    issues.add("Invalid local identifier");error=true;
                }else if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM asset WHERE tenant_id=? AND deleted_at IS NULL AND local_identifier=?)",Boolean.class,input.tenantId(),local)))
                    issues.add("Existing Asset has the same local identifier; review possible re-import");
            }
            for(String field:List.of("lifecycle","condition","loanable")){
                String raw=value(source.values(),columns,field);
                if(!columns.containsKey(field)){
                    normalized.put(field,switch(field){case "lifecycle"->"REGISTERED";case "condition"->"UNKNOWN";default->false;});
                    issues.add("Default "+field+" applied");
                }else if(raw==null || raw.isBlank()){
                    issues.add("Missing "+field+" value");error=true;
                }else{
                    String mapped=valueMaps.getOrDefault(field,Map.of()).getOrDefault(raw,raw).strip().toUpperCase(Locale.ROOT);
                    try{
                        normalized.put(field,switch(field){
                            case "lifecycle"->LifecycleState.valueOf(mapped).name();
                            case "condition"->ConditionState.valueOf(mapped).name();
                            default->switch(mapped){case "TRUE"->true;case "FALSE"->false;default->throw new IllegalArgumentException();};
                        });
                    }catch(IllegalArgumentException ex){issues.add("Unmapped "+field+" value: "+raw);error=true;}
                }
            }
            String rawDate=value(source.values(),columns,"tagDate");
            if(!columns.containsKey("tagDate")){
                normalized.put("tagDate",LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString());
                issues.add("Current UTC Tag date applied");
            }else{
                try{
                    if(rawDate==null || rawDate.isBlank())throw new DateTimeParseException("Missing",String.valueOf(rawDate),0);
                    LocalDate date=parseDate(rawDate.strip());
                    if(date.getYear()<1 || date.getYear()>9999 || date.isAfter(LocalDate.now(clock.withZone(ZoneOffset.UTC))))throw new DateTimeException("Invalid date range");
                    normalized.put("tagDate",date.toString());
                }catch(DateTimeException ex){issues.add("Invalid historical Tag date");error=true;}
            }
            for(String kind:REFERENCES){
                boolean byId=columns.containsKey(kind+"Id");
                boolean byName=columns.containsKey(kind+"Name");
                if(!byId && !byName)continue;
                String target=kind+(byId?"Id":"Name");
                String raw=value(source.values(),columns,target);
                if((raw==null || raw.isBlank()) && kind.equals("owner"))continue;
                if(raw==null || raw.isBlank()){issues.add("Missing "+kind+" reference");error=true;continue;}
                String key=raw.strip();
                var kindEnum=AssetCsvReferenceResolver.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
                UUID chosen=choices.getOrDefault(target,Map.of()).get(key);
                var cache=resolved.computeIfAbsent(target,k->new LinkedHashMap<>());
                var result=cache.computeIfAbsent(key,k->resolver.resolve(kindEnum,input.tenantId(),byId,k,chosen));
                if(result.id()==null){
                    issues.add(kind+" "+result.status().toLowerCase(Locale.ROOT)+": "+key);error=true;
                }else{
                    references.put(kind+"Id",result.id());
                    if(kind.equals("product")){
                        var product=result.candidates().getFirst();
                        if(resolver.lookup(AssetCsvReferenceResolver.Kind.CATEGORY,input.tenantId(),product.categoryId())==null ||
                           resolver.lookup(AssetCsvReferenceResolver.Kind.MANUFACTURER,input.tenantId(),product.manufacturerId())==null){
                            issues.add("Product dependencies are not assignable");error=true;
                        }else{
                            references.put("categoryId",product.categoryId());
                            references.put("manufacturerId",product.manufacturerId());
                        }
                    }
                }
            }
            if(!"REGISTERED".equals(normalized.get("lifecycle")))issues.add("Imported initial Lifecycle bypasses normal transitions");
            if(("DEREGISTERED".equals(normalized.get("lifecycle")) || "DEFECTIVE".equals(normalized.get("condition")) || "UNUSABLE".equals(normalized.get("condition")))
                && Boolean.TRUE.equals(normalized.get("loanable")))issues.add("Blocking state is marked loanable; review intentional legacy value");
            var signature=parsed.headers().stream().map(source.values()::get).toList();
            if(!previous.add(signature))issues.add("Duplicate source row in this file");
            rows.add(new Row(source.rowNumber(),error?"ERROR":issues.isEmpty()?"READY":"WARNING",
                source.values(),Collections.unmodifiableMap(new LinkedHashMap<>(normalized)),Map.copyOf(references),List.copyOf(issues)));
        }
        return new Preview(parsed.sha256(),parsed.encoding(),parsed.delimiter(),parsed.headers(),rows.size(),
            rows.stream().filter(r->r.status().equals("READY")).count(),
            rows.stream().filter(r->r.status().equals("WARNING")).count(),
            rows.stream().filter(r->r.status().equals("ERROR")).count(),List.copyOf(rows),resolved);
    }
    private static Map<String,String> validate(Input input,List<String> headers){
        if(input.columns()==null || input.columns().isEmpty() || !TARGETS.containsAll(input.columns().keySet())
            || !headers.containsAll(input.columns().values()) || new HashSet<>(input.columns().values()).size()!=input.columns().size())
            throw BusinessFailure.invalid("Invalid or duplicate CSV column mapping");
        for(String kind:REFERENCES)if(input.columns().containsKey(kind+"Id") && input.columns().containsKey(kind+"Name"))
            throw BusinessFailure.invalid("UUID and Name modes are mutually exclusive");
        boolean product=input.columns().containsKey("productId")||input.columns().containsKey("productName");
        if(product && (input.columns().keySet().stream().anyMatch(k->k.startsWith("category")||k.startsWith("manufacturer"))))
            throw BusinessFailure.invalid("Product and direct Catalog modes are mutually exclusive");
        if(!product && (!hasReference(input.columns(),"category") || !hasReference(input.columns(),"manufacturer")))
            throw BusinessFailure.invalid("Category and Manufacturer required without Product");
        if(!hasReference(input.columns(),"location"))throw BusinessFailure.invalid("Location required");
        if(input.values()!=null && (!Set.of("lifecycle","condition","loanable").containsAll(input.values().keySet())
            || input.values().values().stream().anyMatch(Objects::isNull)))throw BusinessFailure.invalid("Invalid value mapping");
        if(input.choices()!=null && (input.choices().keySet().stream().anyMatch(k->!k.endsWith("Name") || !input.columns().containsKey(k))
            || input.choices().values().stream().anyMatch(Objects::isNull)))throw BusinessFailure.invalid("Invalid reference choice");
        return input.columns();
    }
    private static boolean hasReference(Map<String,String> columns,String kind){return columns.containsKey(kind+"Id")||columns.containsKey(kind+"Name");}
    private static String value(Map<String,String> source,Map<String,String> columns,String target){return columns.containsKey(target)?source.get(columns.get(target)):null;}
    private static LocalDate parseDate(String value){
        try{return LocalDate.parse(value,DateTimeFormatter.ISO_LOCAL_DATE);}
        catch(DateTimeParseException ex){return LocalDate.parse(value,DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT));}
    }
}
