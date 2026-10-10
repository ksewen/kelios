package com.github.ksewen.kelios.identity.auth;

import java.util.*;

/** Official IServ contract: scope/claim iserv:groups, list of objects with UUID id. No role mapping. */
public final class IServGroups {
    private IServGroups() {}
    public static Set<UUID> parse(Map<String,Object> userInfo){
        Object raw=userInfo.get("iserv:groups");if(!(raw instanceof List<?> groups) || groups.size()>4096)throw AuthFailure.denied();
        Set<UUID> ids=new HashSet<>();
        for(Object item:groups){
            if(!(item instanceof Map<?,?> group) || !(group.get("id") instanceof String value)
                || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw AuthFailure.denied();
            ids.add(UUID.fromString(value));
        }
        return Set.copyOf(ids);
    }
    public static void requireAllowed(Set<UUID> groups,Set<UUID> allowed){if(Collections.disjoint(groups,allowed))throw AuthFailure.denied();}
}
