package com.github.ksewen.kelios.audit;

import java.time.Instant;
import java.util.*;

/** Immutable historical facts; no live User/target association or transport/entity dependency. */
public record AuditRecord(UUID id,UUID tenantId,UUID actorId,String actorDisplayName,String action,String targetType,UUID targetId,Instant occurredAt,Map<String,Object> data) {}
