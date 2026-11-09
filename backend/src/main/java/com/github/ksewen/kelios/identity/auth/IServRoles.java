package com.github.ksewen.kelios.identity.auth;

import java.util.*;

/** Only the documented displayName grants authority; opaque role IDs are ignored. */
public final class IServRoles {
    private IServRoles() {}
    public static Set<String> parse(Map<String,Object> userInfo){
        Object raw=userInfo.get("iserv:roles");
        if(!(raw instanceof List<?> list) || list.size()>4096)throw AuthFailure.denied();
        Set<String> names=new HashSet<>();
        for(Object item:list){
            if(!(item instanceof Map<?,?> role) || !(role.get("displayName") instanceof String name)
                || name.isBlank() || name.length()>256 || !name.equals(name.strip())
                || name.chars().anyMatch(Character::isISOControl) || !names.add(name))throw AuthFailure.denied();
        }
        return Set.copyOf(names);
    }
}
