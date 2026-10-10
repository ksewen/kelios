package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.identity.UserService.*;
import java.util.Objects;
import java.util.UUID;

/** Explicit approved matrix; independent of HTTP, entities and the state-machine engine. */
public final class Authorization {
    private Authorization() {}
    public static void active(Identity actor) { if(actor==null || actor.status()!=Status.ACTIVE) throw AuthFailure.denied(); }
    public static void globalAdmin(Identity actor) { active(actor); if(!actor.globalAdmin())throw AuthFailure.denied(); }
    public static void administrator(Identity actor) { active(actor); if(actor.role()!=Role.ADMIN)throw AuthFailure.denied(); }
    public static void readTenant(Identity actor, UUID tenant) {
        active(actor); if(tenant==null || (actor.scope()==Scope.TENANT && !Objects.equals(actor.tenantId(),tenant)))throw AuthFailure.denied();
    }
    public static void writeTenant(Identity actor, UUID tenant) {
        readTenant(actor,tenant); if(actor.role()!=Role.ADMIN)throw AuthFailure.denied();
    }
    public static void writeCatalog(Identity actor, UUID tenant) { if(tenant==null)globalAdmin(actor);else writeTenant(actor,tenant); }
    public static void manageUser(Identity actor, Identity target) {
        active(actor); if(actor.globalAdmin())return;
        if(actor.scope()!=Scope.TENANT || actor.role()!=Role.ADMIN || target.scope()!=Scope.TENANT
            || !Objects.equals(actor.tenantId(),target.tenantId()) || target.role()!=Role.USER)throw AuthFailure.denied();
    }
    public static void destructiveSelf(Identity actor, UUID target) { if(actor.id().equals(target))throw AuthFailure.denied(); }
}
