package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.auth.OidcConfigurationService;
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
    record Detail(UUID tenantId,String issuer,String clientId,boolean secretConfigured,boolean enabled,long version,Set<UUID> allowedGroupIds,String callbackUri,Metadata metadata,Tenant tenant){}
    static Detail detail(OidcConfigurationService.View v){var m=v.metadata();return new Detail(v.tenantId(),v.issuer(),v.clientId(),v.secretConfigured(),v.enabled(),v.version(),v.allowedGroupIds(),v.callbackUri(),new Metadata(m.createdBy(),m.createdAt(),m.updatedBy(),m.updatedAt(),SystemActor.displayName(m.createdBy()),SystemActor.displayName(m.updatedBy())),new Tenant(v.tenant().id(),v.tenant().name()));}
    static long deleteVersion(Map<String,String> query){
        if(!query.keySet().equals(Set.of("version")))throw new IllegalArgumentException("Provider version required");
        try{long version=Long.parseLong(query.get("version"));if(version<0)throw new IllegalArgumentException();return version;}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Non-negative provider version required");}
    }
    record Configure(String issuer,String clientId,String clientSecret,boolean enabled,Long version,Set<UUID> allowedGroupIds){
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static Configure bind(Map<String,Object> body){
            var c=new CommandInput(body,"issuer","clientId","clientSecret","enabled","version","allowedGroupIds");
            Object version=body.get("version");if(version!=null && (!(version instanceof Integer || version instanceof Long) || ((Number)version).longValue()<0))throw new IllegalArgumentException("Non-negative provider version required");
            Object values=body.get("allowedGroupIds");if(!(values instanceof List<?> list) || list.size()>256)throw new IllegalArgumentException("Allowed group UUIDs required");
            var groups=new HashSet<UUID>();for(Object value:list){if(!(value instanceof String text))throw new IllegalArgumentException("Allowed group UUIDs required");UUID id=ListInput.uuid(text);if(id==null)throw new IllegalArgumentException("Allowed group UUIDs required");groups.add(id);}
            return new Configure(c.text("issuer"),c.text("clientId"),c.text("clientSecret"),c.bool("enabled"),version==null?null:((Number)version).longValue(),Set.copyOf(groups));
        }
        @Override public String toString(){return "Configure[redacted]";}
    }
}
