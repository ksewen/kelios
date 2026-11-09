package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.auth.OidcConfigurationService;
import com.github.ksewen.kelios.identity.UserService.Role;
import com.github.ksewen.kelios.audit.SystemActor;
import com.github.ksewen.kelios.platform.ListInput;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.Instant;
import java.util.*;

/** Tenant singleton transport, separate from runtime Provider credentials and application projections. */
final class OidcConfigurationDtos {
    private OidcConfigurationDtos(){}
    record Tenant(UUID id,String name){}
    record Metadata(UUID createdBy,Instant createdAt,UUID updatedBy,Instant updatedAt,String createdByDisplayName,String updatedByDisplayName){}
    record Detail(UUID tenantId,String issuer,String clientId,boolean secretConfigured,boolean enabled,long version,List<OidcConfigurationService.GroupMapping> groupMappings,List<OidcConfigurationService.RoleMapping> roleMappings,String callbackUri,Metadata metadata,Tenant tenant){}
    record Summary(UUID tenantId,Tenant tenant,String issuer,String clientId,boolean enabled,long version){}
    static com.github.ksewen.kelios.platform.Page<Summary> page(OidcConfigurationService.ConfigurationPage page){return new com.github.ksewen.kelios.platform.Page<>(page.items().stream().map(v->new Summary(v.tenantId(),new Tenant(v.tenant().id(),v.tenant().name()),v.issuer(),v.clientId(),v.enabled(),v.version())).toList(),page.page(),page.size(),page.totalElements());}
    record Query(int page,int size,String q,Boolean enabled){}
    static Query query(Map<String,String> query){
        if(!Set.of("page","size","q","enabled").containsAll(query.keySet()))throw new IllegalArgumentException("Unsupported configuration query parameter");
        var values=new HashMap<>(query);values.putIfAbsent("size","20");
        String q=values.getOrDefault("q","");if(q.length()>128)throw new IllegalArgumentException("Configuration search exceeds 128 characters");
        Boolean enabled=values.containsKey("enabled")?ListInput.bool(values.get("enabled")):null;
        return new Query(ListInput.page(values),ListInput.size(values),q,enabled);
    }
    static Detail detail(OidcConfigurationService.View v){var m=v.metadata();return new Detail(v.tenantId(),v.issuer(),v.clientId(),v.secretConfigured(),v.enabled(),v.version(),v.groupMappings(),v.roleMappings(),v.callbackUri(),new Metadata(m.createdBy(),m.createdAt(),m.updatedBy(),m.updatedAt(),SystemActor.displayName(m.createdBy()),SystemActor.displayName(m.updatedBy())),new Tenant(v.tenant().id(),v.tenant().name()));}
    static long deleteVersion(Map<String,String> query){
        if(!query.keySet().equals(Set.of("version")))throw new IllegalArgumentException("Provider version required");
        try{long version=Long.parseLong(query.get("version"));if(version<0)throw new IllegalArgumentException();return version;}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Non-negative provider version required");}
    }
    record SecretInput(String clientSecret,long version){
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static SecretInput bind(Map<String,Object> body){
            var c=new CommandInput(body,"clientSecret","version");
            c.present("clientSecret");c.present("version");
            Object version=body.get("version");
            if(!(version instanceof Integer || version instanceof Long) || ((Number)version).longValue()<0)throw new IllegalArgumentException("Non-negative provider version required");
            return new SecretInput(c.text("clientSecret"),((Number)version).longValue());
        }
        @Override public String toString(){return "SecretInput[redacted]";}
    }
    record Configure(String issuer,String clientId,String clientSecret,boolean enabled,Long version,List<OidcConfigurationService.GroupMapping> groupMappings,List<OidcConfigurationService.RoleMapping> roleMappings){
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static Configure bind(Map<String,Object> body){
            var c=new CommandInput(body,"issuer","clientId","clientSecret","enabled","version","groupMappings","roleMappings");
            Object version=body.get("version");if(version!=null && (!(version instanceof Integer || version instanceof Long) || ((Number)version).longValue()<0))throw new IllegalArgumentException("Non-negative provider version required");
            Object values=body.get("groupMappings");if(!(values instanceof List<?> list) || list.size()>256)throw new IllegalArgumentException("Group mappings required");
            var groups=new ArrayList<OidcConfigurationService.GroupMapping>();var seen=new HashSet<String>();
            for(Object value:list){
                if(!(value instanceof Map<?,?> item) || !item.keySet().equals(Set.of("act","role")) || !(item.get("act") instanceof String act)
                    || !(item.get("role") instanceof String role) || !Set.of("USER","ADMIN").contains(role) || !seen.add(act))throw new IllegalArgumentException("Valid unique group mappings required");
                groups.add(new OidcConfigurationService.GroupMapping(act,Role.valueOf(role)));
            }
            Object roleValues=body.get("roleMappings");if(body.containsKey("roleMappings") && !(roleValues instanceof List<?>))throw new IllegalArgumentException("Role mappings must be a list");
            List<OidcConfigurationService.RoleMapping> roles=null;
            if(roleValues instanceof List<?> roleList){
                if(roleList.size()>256)throw new IllegalArgumentException("Too many role mappings");
                var parsed=new ArrayList<OidcConfigurationService.RoleMapping>();var roleNames=new HashSet<String>();
                for(Object value:roleList){
                    if(!(value instanceof Map<?,?> item) || !item.keySet().equals(Set.of("displayName","role")) || !(item.get("displayName") instanceof String name)
                        || !(item.get("role") instanceof String role) || !Set.of("USER","ADMIN").contains(role) || !roleNames.add(name))throw new IllegalArgumentException("Valid unique role mappings required");
                    parsed.add(new OidcConfigurationService.RoleMapping(name,Role.valueOf(role)));
                }
                roles=List.copyOf(parsed);
            }
            return new Configure(c.text("issuer"),c.text("clientId"),c.text("clientSecret"),c.bool("enabled"),version==null?null:((Number)version).longValue(),List.copyOf(groups),roles);
        }
        @Override public String toString(){return "Configure[redacted]";}
    }
}
