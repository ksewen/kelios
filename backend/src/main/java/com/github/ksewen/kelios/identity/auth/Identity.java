package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.audit.Actor;
import com.github.ksewen.kelios.identity.UserService.*;
import java.security.Principal;
import java.util.UUID;

public record Identity(UUID id, Scope scope, UUID tenantId, Role role, Status status, String displayName) implements Principal {
    public Identity {
        if(id==null || scope==null || role==null || status==null || displayName==null || displayName.isBlank()
            || (scope==Scope.GLOBAL)!=(tenantId==null)) throw AuthFailure.invalid();
    }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getName() { return id.toString(); }
    public Actor actor() { return new Actor(id,displayName); }
    public boolean globalAdmin() { return scope==Scope.GLOBAL && role==Role.ADMIN; }
}
