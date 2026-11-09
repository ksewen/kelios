package com.github.ksewen.kelios.identity.auth;

import java.util.*;
import org.slf4j.*;

/** DEBUG-only protocol shape and fixed outcomes; never log provider values or request URLs. */
final class OidcDiagnostics {
    static final Logger LOG=LoggerFactory.getLogger("kelios.oidc.diagnostics");
    private OidcDiagnostics() {}
    static void rejected(String stage,String reason,int status){if(LOG.isDebugEnabled())LOG.debug("OIDC stage={} reason={} status={}",stage,reason,status);}
    static void http(String stage,int status){if(LOG.isDebugEnabled())LOG.debug("OIDC stage={} httpStatus={}",stage,status);}
    static void transport(String stage,Exception failure){if(LOG.isDebugEnabled())LOG.debug("OIDC stage={} transport={}",stage,failure.getClass().getSimpleName());}
    static void userInfo(Map<String,Object> info){
        if(!LOG.isDebugEnabled())return;
        Object raw=info.get("iserv:groups");int count=raw instanceof List<?> list?list.size():-1;
        Object first=raw instanceof List<?> list&&!list.isEmpty()?list.getFirst():null;
        LOG.debug("OIDC stage=userinfo fields={} groupCount={} groupItemFields={}",fields(info),count,first instanceof Map<?,?> map?fields(map):type(first));
        Object rawRoles=info.get("iserv:roles");Object firstRole=rawRoles instanceof List<?> roles&&!roles.isEmpty()?roles.getFirst():null;
        LOG.debug("OIDC stage=userinfo roleCount={} roleItemFields={}",rawRoles instanceof List<?> roles?roles.size():-1,firstRole instanceof Map<?,?> map?fields(map):type(firstRole));
        if(raw instanceof Map<?,?> object){
            Object value=object.isEmpty()?null:object.values().iterator().next();
            Object item=value instanceof List<?> list&&!list.isEmpty()?list.getFirst():value;
            LOG.debug("OIDC stage=userinfo groupObjectCount={} groupObjectFields={} firstValueType={} firstValueCount={} firstValueFields={}",
                object.size(),groupFields(object),type(value),value instanceof List<?> list?list.size():-1,item instanceof Map<?,?> map?groupFields(map):List.of());
            LOG.debug("OIDC stage=userinfo groupKeyFormats={} groupIdFormats={} groupActFormats={} groupNameFormats={}",
                formats(object.keySet()),formats(object.values(),"id"),formats(object.values(),"act"),formats(object.values(),"name"));
        }
    }
    static void groups(int observed,int allowed,boolean matched){if(LOG.isDebugEnabled())LOG.debug("OIDC stage=groups observedCount={} allowedCount={} intersects={}",observed,allowed,matched);}
    static void roles(int observed,int allowed,boolean matched){if(LOG.isDebugEnabled())LOG.debug("OIDC stage=roles observedCount={} allowedCount={} intersects={}",observed,allowed,matched);}
    static String fields(Map<?,?> map){
        return map.entrySet().stream().limit(24).map(e->safeKey(e.getKey())+":"+type(e.getValue())).sorted().toList().toString();
    }
    private static List<String> groupFields(Map<?,?> map){return List.of("id","act","name").stream().filter(map::containsKey).map(key->key+":"+type(map.get(key))).toList();}
    private static Map<String,Integer> formats(Collection<?> values){
        Map<String,Integer> counts=new TreeMap<>();for(Object value:values)counts.merge(format(value),1,Integer::sum);return counts;
    }
    private static Map<String,Integer> formats(Collection<?> groups,String field){
        return formats(groups.stream().map(value->value instanceof Map<?,?> map?map.get(field):null).toList());
    }
    private static String format(Object value){
        if(value instanceof String text){
            if(text.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))return "uuid";
            return text.isBlank()?"blank":"non_uuid_string";
        }
        return type(value);
    }
    private static String safeKey(Object key){String value=key instanceof String s?s:"<non-string>";return value.substring(0,Math.min(value.length(),48)).replaceAll("[^A-Za-z0-9:_-]","_");}
    private static String type(Object value){return value==null?"null":value instanceof String?"string":value instanceof List?"array":value instanceof Map?"object":value instanceof Number?"number":value instanceof Boolean?"boolean":"other";}
}
