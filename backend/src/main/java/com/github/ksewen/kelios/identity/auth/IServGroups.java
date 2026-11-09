package com.github.ksewen.kelios.identity.auth;

import java.util.*;
import com.github.ksewen.kelios.identity.UserService.Role;

/** Match the verified group account, never a display label or provider-specific opaque ID. */
public final class IServGroups {
    private IServGroups() {}
    public static Set<String> parse(Map<String,Object> userInfo){
        Object raw=userInfo.get("iserv:groups");
        int count=raw instanceof List<?> list?list.size():raw instanceof Map<?,?> map?map.size():-1;
        if(count<0 || count>4096){OidcDiagnostics.rejected("groups","missing_or_invalid_claim",403);throw AuthFailure.denied();}
        Set<String> accounts=new HashSet<>();
        if(raw instanceof List<?> list)for(Object item:list)add(item,null,accounts);
        else if(raw instanceof Map<?,?> map)for(var entry:map.entrySet()){
            if(!(entry.getKey() instanceof String key)){OidcDiagnostics.rejected("groups","invalid_group_item",403);throw AuthFailure.denied();}
            add(entry.getValue(),key,accounts);
        }
        return Set.copyOf(accounts);
    }
    private static void add(Object item,String expectedId,Set<String> accounts){
        if(!(item instanceof Map<?,?> group) || !(group.get("id") instanceof String id) || !valid(id)
            || expectedId!=null && !expectedId.equals(id) || !(group.get("act") instanceof String act) || !valid(act) || !accounts.add(act)){
            OidcDiagnostics.rejected("groups","invalid_group_item",403);throw AuthFailure.denied();
        }
    }
    private static boolean valid(String value){return !value.isBlank() && value.length()<=256 && value.equals(value.strip()) && value.chars().noneMatch(Character::isISOControl);}
    public static Role mappedRole(Set<String> accounts,List<OidcConfigurationService.GroupMapping> mappings){
        boolean matched=false,admin=false;for(var mapping:mappings)if(accounts.contains(mapping.act())){matched=true;if(mapping.role()==Role.ADMIN)admin=true;}
        OidcDiagnostics.groups(accounts.size(),mappings.size(),matched);
        if(!matched){OidcDiagnostics.rejected("groups","no_allowed_intersection",403);throw AuthFailure.denied();}
        return admin?Role.ADMIN:Role.USER;
    }
}
