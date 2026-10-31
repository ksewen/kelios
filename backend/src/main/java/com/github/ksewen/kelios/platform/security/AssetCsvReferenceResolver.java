package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.platform.BusinessFailure;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Exact, active, Tenant-visible lookup for import Preview; no fuzzy match or creation. */
@Component
public class AssetCsvReferenceResolver {
    enum Kind { TENANT, PRODUCT, CATEGORY, MANUFACTURER, LOCATION, OWNER }
    public record Candidate(UUID id,String name,UUID tenantId,UUID categoryId,UUID manufacturerId) {}
    public record Result(String status,UUID id,List<Candidate> candidates,boolean more) {}
    private final JdbcTemplate jdbc;
    public AssetCsvReferenceResolver(JdbcTemplate jdbc){this.jdbc=jdbc;}

    Result resolve(Kind kind,UUID tenant,boolean byId,String raw,UUID chosen){
        String value=raw==null?"":raw.strip();
        if(value.isEmpty())return new Result("MISSING",null,List.of(),false);
        if(byId){
            if(chosen!=null)throw BusinessFailure.invalid("UUID references cannot have a Name choice");
            try{
                UUID id=UUID.fromString(value);
                if(!id.toString().equalsIgnoreCase(value))return new Result("INVALID_UUID",null,List.of(),false);
                Candidate candidate=lookup(kind,tenant,id);
                return candidate==null?new Result("UNRESOLVED",null,List.of(),false)
                    :new Result("RESOLVED",candidate.id(),List.of(candidate),false);
            }catch(IllegalArgumentException ex){return new Result("INVALID_UUID",null,List.of(),false);}
        }
        if(chosen!=null){
            Candidate candidate=lookup(kind,tenant,chosen);
            return candidate==null || !candidate.name().equalsIgnoreCase(value)?new Result("UNRESOLVED",null,List.of(),false)
                :new Result("CHOSEN",candidate.id(),List.of(candidate),false);
        }
        var matches=kind==Kind.TENANT
            ?jdbc.query(select(kind)+" WHERE lower(name)=lower(?) ORDER BY name,id LIMIT 21",
                (rs,n)->candidate(rs,kind),value)
            :jdbc.query(select(kind)+" WHERE deleted_at IS NULL AND lower(name)=lower(?)"+scope(kind)+" ORDER BY name,id LIMIT 21",
                (rs,n)->candidate(rs,kind),value,tenant);
        if(matches.isEmpty())return new Result("UNRESOLVED",null,List.of(),false);
        boolean more=matches.size()>20;
        var candidates=List.copyOf(matches.subList(0,Math.min(20,matches.size())));
        return matches.size()==1?new Result("RESOLVED",matches.getFirst().id(),candidates,false)
            :new Result("AMBIGUOUS",null,candidates,more);
    }
    Candidate lookup(Kind kind,UUID tenant,UUID id){
        var rows=kind==Kind.TENANT
            ?jdbc.query(select(kind)+" WHERE id=?",(rs,n)->candidate(rs,kind),id)
            :jdbc.query(select(kind)+" WHERE id=? AND deleted_at IS NULL"+scope(kind),
                (rs,n)->candidate(rs,kind),id,tenant);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private static String select(Kind kind){
        String table=kind.name().toLowerCase(Locale.ROOT);
        return "SELECT id,name,"+(kind==Kind.TENANT?"NULL::uuid AS tenant_id":"tenant_id")+
            (kind==Kind.PRODUCT?",category_id,manufacturer_id":"")+" FROM "+table;
    }
    private static String scope(Kind kind){
        return kind==Kind.LOCATION||kind==Kind.OWNER?" AND tenant_id=?":" AND (tenant_id IS NULL OR tenant_id=?)";
    }
    private static Candidate candidate(java.sql.ResultSet rs,Kind kind)throws java.sql.SQLException{
        return new Candidate(rs.getObject("id",UUID.class),rs.getString("name"),rs.getObject("tenant_id",UUID.class),
            kind==Kind.PRODUCT?rs.getObject("category_id",UUID.class):null,
            kind==Kind.PRODUCT?rs.getObject("manufacturer_id",UUID.class):null);
    }
}
