package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.audit.*;
import com.github.ksewen.kelios.platform.*;
import java.util.*;
import java.time.*;

final class AuditDtos {
    private AuditDtos(){}
    static AuditQuery query(Map<String,String> q){
        ListInput.keys(q,"action","targetType","targetId","actorId","tenantId","globalOnly","from","until","sort","direction");
        if(q.containsKey("q"))throw BusinessFailure.invalid("Audit has no text search");
        if(q.containsKey("sort") && !"occurredAt".equals(q.get("sort")))throw BusinessFailure.invalid("Unsupported Audit sort");
        AuditQuery.Direction direction=q.containsKey("direction")?switch(q.get("direction")){case "asc"->AuditQuery.Direction.ASC;case "desc"->AuditQuery.Direction.DESC;default->throw BusinessFailure.invalid("Invalid direction");}:null;
        return new AuditQuery(ListInput.page(q),ListInput.size(Map.of("size",q.getOrDefault("size","20"))),q.get("action"),q.get("targetType"),ListInput.uuid(q.get("targetId")),ListInput.uuid(q.get("actorId")),ListInput.uuid(q.get("tenantId")),q.containsKey("globalOnly")?ListInput.bool(q.get("globalOnly")):null,time(q.get("from")),time(q.get("until")),direction);
    }
    private static Instant time(String value){try{return value==null?null:OffsetDateTime.parse(value).toInstant();}catch(java.time.DateTimeException ex){throw BusinessFailure.invalid("Audit bound requires an offset/UTC timestamp");}}
    record Item(UUID id,UUID tenantId,UUID actorId,String actorDisplayName,String action,String targetType,UUID targetId,Instant occurredAt,Map<String,Object> data){}
    static Item item(AuditRecord r){return new Item(r.id(),r.tenantId(),r.actorId(),r.actorDisplayName(),r.action(),r.targetType(),r.targetId(),r.occurredAt(),r.data());}
    static Page<Item> page(Page<AuditRecord> p){return new Page<>(p.items().stream().map(AuditDtos::item).toList(),p.page(),p.size(),p.totalElements());}
}
