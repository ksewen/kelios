package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.*;
import com.github.ksewen.kelios.identity.UserService.*;
import com.github.ksewen.kelios.identity.auth.Identity;
import com.github.ksewen.kelios.platform.*;
import java.util.*;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonCreator;

/** Resource-specific transport mappings; credentials and external provider claims never enter projections. */
final class UserDtos {
    private UserDtos() {}
    static UserQuery query(Map<String,String> q){
        ListInput.keys(q,"scope","role","status","tenantId","sort","direction");
        UserQuery.Sort sort=q.containsKey("sort")?switch(q.get("sort")){case "displayName"->UserQuery.Sort.DISPLAY_NAME;case "scope"->UserQuery.Sort.SCOPE;case "role"->UserQuery.Sort.ROLE;case "status"->UserQuery.Sort.STATUS;default->throw BusinessFailure.invalid("Unsupported User sort");}:null;
        UserQuery.Direction direction=q.containsKey("direction")?switch(q.get("direction")){case "asc"->UserQuery.Direction.ASC;case "desc"->UserQuery.Direction.DESC;default->throw BusinessFailure.invalid("Invalid direction");}:null;
        return new UserQuery(ListInput.page(q),ListInput.size(Map.of("size",q.getOrDefault("size","20"))),q.get("q"),state(q,"scope",Scope.class),state(q,"role",Role.class),state(q,"status",Status.class),ListInput.uuid(q.get("tenantId")),sort,direction);
    }
    private static <E extends Enum<E>> E state(Map<String,String> q,String key,Class<E> type){if(!q.containsKey(key))return null;try{return Enum.valueOf(type,q.get(key));}catch(IllegalArgumentException ex){throw BusinessFailure.invalid("Invalid "+key);}}
    record Tenant(UUID id,String name) {}
    record Metadata(UUID createdBy,Instant createdAt,UUID updatedBy,Instant updatedAt,String createdByDisplayName,String updatedByDisplayName) {}
    record Item(UUID id,String scope,UUID tenantId,String role,String status,String displayName,String email,Tenant tenant,Metadata metadata) {}
    record Detail(UUID id,String scope,UUID tenantId,String role,String status,String displayName,Tenant tenant,Metadata metadata,GlobalAccount globalAccount) {}
    record GlobalAccount(String email,Instant emailVerifiedAt) {}
    record State(UUID id,String scope,UUID tenantId,String role,String status,String displayName) {}
    private static Tenant tenant(UserModel u){return u.tenant()==null?null:new Tenant(u.tenant().id(),u.tenant().name());}
    private static UUID tenantId(UserModel u){return u.tenant()==null?null:u.tenant().id();}
    private static Metadata metadata(UserModel u){var m=u.metadata();return new Metadata(m.createdBy(),m.createdAt(),m.updatedBy(),m.updatedAt(),m.createdByDisplayName()==null?com.github.ksewen.kelios.audit.SystemActor.displayName(m.createdBy()):m.createdByDisplayName(),com.github.ksewen.kelios.audit.SystemActor.displayName(m.updatedBy()));}
    static Page<Item> page(Page<UserModel> p){return new Page<>(p.items().stream().map(u->new Item(u.id(),u.scope().name(),tenantId(u),u.role().name(),u.status().name(),u.displayName(),u.scope()==Scope.GLOBAL&&u.globalAccount()!=null?u.globalAccount().email():null,tenant(u),metadata(u))).toList(),p.page(),p.size(),p.totalElements());}
    static Detail detail(UserModel u){return new Detail(u.id(),u.scope().name(),tenantId(u),u.role().name(),u.status().name(),u.displayName(),tenant(u),metadata(u),u.globalAccount()==null?null:new GlobalAccount(u.globalAccount().email(),u.globalAccount().emailVerifiedAt()));}
    static State state(Identity u){return new State(u.id(),u.scope().name(),u.tenantId(),u.role().name(),u.status().name(),u.displayName());}
    record GlobalInput(String email,String displayName,String password,Role role){
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static GlobalInput bind(Map<String,Object> b){var c=new CommandInput(b,"email","displayName","password","role");if(b.containsKey("role")&&b.get("role")==null)throw BusinessFailure.invalid("Role must be USER or ADMIN");return new GlobalInput(c.text("email"),c.text("displayName"),c.text("password"),c.state("role",Role.class,false));}
        @Override public String toString(){return "GlobalInput[redacted]";}
    }
    record AdministrationInput(String email,Role role){
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static AdministrationInput bind(Map<String,Object> b){
            var c=new CommandInput(b,"email","role");if(b.isEmpty()||b.containsKey("email")&&b.get("email")==null||b.containsKey("role")&&b.get("role")==null)throw BusinessFailure.invalid("Changed Email or role required");
            return new AdministrationInput(c.text("email"),c.state("role",Role.class,false));
        }
        @Override public String toString(){return "AdministrationInput[redacted]";}
    }
    record EmailInput(String email){@JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static EmailInput bind(Map<String,Object> b){var c=new CommandInput(b,"email");return new EmailInput(c.text("email"));} @Override public String toString(){return "EmailInput[redacted]";}}
    record RoleInput(Role role){@JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static RoleInput bind(Map<String,Object> b){var c=new CommandInput(b,"role");return new RoleInput(c.state("role",Role.class,true));}}
    record CredentialInput(String email,String password){
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING) public static CredentialInput bind(Map<String,Object> b){var c=new CommandInput(b,"email","password");return new CredentialInput(c.text("email"),c.text("password"));}
        @Override public String toString(){return "CredentialInput[redacted]";}
    }
}
