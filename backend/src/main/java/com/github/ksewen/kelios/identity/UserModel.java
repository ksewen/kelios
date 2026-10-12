package com.github.ksewen.kelios.identity;

import com.github.ksewen.kelios.identity.UserService.*;
import java.time.Instant;
import java.util.UUID;

/** Safe administrative read model, separate from authentication principal and persistence entity. */
public record UserModel(UUID id,Scope scope,Role role,Status status,String displayName,Tenant tenant,Metadata metadata,GlobalAccount globalAccount) {
    public UserModel(UUID id,Scope scope,Role role,Status status,String displayName,Tenant tenant,Metadata metadata){this(id,scope,role,status,displayName,tenant,metadata,null);}
    public record GlobalAccount(String email,Instant emailVerifiedAt) {}
    public record Tenant(UUID id,String name) {}
    public record Metadata(UUID createdBy,Instant createdAt,UUID updatedBy,Instant updatedAt) {}
}
