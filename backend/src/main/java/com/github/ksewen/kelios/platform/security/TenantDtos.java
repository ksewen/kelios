package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.tenancy.TenantModel;
import com.github.ksewen.kelios.audit.SystemActor;
import com.github.ksewen.kelios.identity.auth.AuthFailure;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.Instant;
import java.util.*;

/** Explicit Tenant transport: no credentials, provider routing or entity serialization. */
final class TenantDtos {
    private TenantDtos(){}
    static void query(Map<String,String> query){if(!query.isEmpty())throw new AuthFailure(400,"Tenant collection has no query parameters");}
    record Metadata(UUID createdBy,Instant createdAt,UUID updatedBy,Instant updatedAt,String createdByDisplayName,String updatedByDisplayName){}
    record Item(UUID id,String name,Metadata metadata){}
    record Detail(UUID id,String name,Metadata metadata){}
    private static Metadata metadata(TenantModel t){var m=t.metadata();return new Metadata(m.createdBy(),m.createdAt(),m.updatedBy(),m.updatedAt(),SystemActor.displayName(m.createdBy()),SystemActor.displayName(m.updatedBy()));}
    static Item item(TenantModel t){return new Item(t.id(),t.name(),metadata(t));}
    static Detail detail(TenantModel t){return new Detail(t.id(),t.name(),metadata(t));}
    record Create(String name){@JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static Create bind(Map<String,Object> b){var input=new CommandInput(b,"name");return new Create(input.text("name"));}}
}
