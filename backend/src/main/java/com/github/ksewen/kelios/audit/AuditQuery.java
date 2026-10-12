package com.github.ksewen.kelios.audit;

import com.github.ksewen.kelios.platform.BusinessFailure;
import java.time.Instant;
import java.util.UUID;

/** Closed timeline criteria; requested narrowing never supplies authority. */
public record AuditQuery(int page,int size,String action,String targetType,UUID targetId,UUID actorId,UUID tenantId,Boolean globalOnly,Instant from,Instant until,Direction direction) {
    public enum Direction { ASC, DESC }
    public AuditQuery {
        if(page<0 || size<1 || size>100)throw BusinessFailure.invalid("Invalid pagination");
        if(tenantId!=null && globalOnly!=null)throw BusinessFailure.invalid("Incompatible audit filters");
        if(from!=null && until!=null && from.isAfter(until))throw BusinessFailure.invalid("Reversed Audit time range");
        // PostgreSQL timestamptz precision: do not round a caller's inclusive boundary silently.
        for(Instant bound:new Instant[]{from,until})if(bound!=null && bound.getNano()%1000!=0)throw BusinessFailure.invalid("Audit time bounds require microsecond precision");
        if(direction==null)direction=Direction.DESC;
    }
}
