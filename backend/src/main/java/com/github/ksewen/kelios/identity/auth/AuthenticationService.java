package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.audit.AuditService;
import com.github.ksewen.kelios.identity.UserService.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthenticationService {
    private final JdbcTemplate jdbc;private final IdentityStore users;private final Passwords passwords;
    private final java.time.Clock clock;private final AuditService audit;private final JwtTokens tokens;private final TransactionTemplate tx;
    public AuthenticationService(JdbcTemplate jdbc,IdentityStore users,Passwords passwords,AuditService audit,JwtTokens tokens,PlatformTransactionManager tm,java.time.Clock clock){
        this.clock=clock;this.jdbc=jdbc;this.users=users;this.passwords=passwords;this.audit=audit;this.tokens=tokens;this.tx=new TransactionTemplate(tm);
    }
    private String name(String value){if(value==null || value.isBlank() || value.length()>256)throw new AuthFailure(400,"Display name required");return value.strip();}
    private void credential(UUID user,String email,String hash){jdbc.update("INSERT INTO local_global_credential(user_id,email,password_hash,email_verified_at) VALUES(?,?,?,?)",user,email,hash,java.sql.Timestamp.from(clock.instant()));}
    public Identity bootstrap(String email,String displayName,String password){
        String canonical=passwords.email(email),display=name(displayName),hash=passwords.hash(password);
        return tx.execute(s->{
            users.recoveryGuard();
            var credentials=jdbc.queryForList("SELECT c.email,c.password_hash,c.email_verified_at FROM app_user u JOIN local_global_credential c ON c.user_id=u.id WHERE u.scope='GLOBAL' AND u.role='ADMIN' AND u.status='ACTIVE' AND c.email_verified_at IS NOT NULL");
            if(credentials.stream().anyMatch(this::usableCredential))throw new AuthFailure(409,"A usable GLOBAL administrator already exists");
            var user=new Identity(UUID.randomUUID(),Scope.GLOBAL,null,Role.ADMIN,Status.ACTIVE,display);
            users.insert(user,user.actor());credential(user.id(),canonical,hash);
            audit.record(user.actor(),user.tenantId(),"GLOBAL_ADMIN_BOOTSTRAPPED","USER",user.id(),Map.of("scope","GLOBAL"));return user;
        });
    }
    public JwtTokens.Pair login(String email,String password){
        String canonical;
        try{canonical=passwords.email(email);}catch(AuthFailure ex){passwords.matches(password,null);throw AuthFailure.invalid();}
        var rows=jdbc.queryForList("SELECT user_id,password_hash FROM local_global_credential WHERE lower(email)=?",canonical);
        String hash=rows.isEmpty()?null:(String)rows.getFirst().get("password_hash");
        if(!passwords.matches(password,hash))throw AuthFailure.invalid();
        UUID id=(UUID)rows.getFirst().get("user_id");
        return tx.execute(s->{
            Identity user=users.load(id," FOR SHARE");
            var current=jdbc.queryForList("SELECT email,password_hash FROM local_global_credential WHERE user_id=? FOR SHARE",id);
            if(user.scope()!=Scope.GLOBAL || current.isEmpty() || !hash.equals(current.getFirst().get("password_hash")) || !canonical.equals(current.getFirst().get("email")))throw AuthFailure.invalid();
            if(user.status()!=Status.ACTIVE || !verifiedCredential(id))throw AuthFailure.denied();return tokens.login(user);
        });
    }
    public JwtTokens.Pair refresh(String refresh){
        var verified=tokens.verify(refresh,"refresh");
        return tx.execute(s->{
            Identity user;
            try{user=users.load(verified.userId()," FOR SHARE");}catch(AuthFailure ex){throw AuthFailure.invalid();}
            if(user.status()==Status.DISABLED || (user.scope()==Scope.GLOBAL && (user.status()!=Status.ACTIVE || !verifiedCredential(user.id()))))throw AuthFailure.denied();return tokens.refresh(user,refresh);
        });
    }
    public Identity createGlobal(Identity actor,String email,String displayName,String password){
        Authorization.globalAdmin(actor);String canonical=passwords.email(email),display=name(displayName),hash=passwords.hash(password);
        return tx.execute(s->{
            var user=new Identity(UUID.randomUUID(),Scope.GLOBAL,null,Role.USER,Status.ACTIVE,display);users.insert(user,actor.actor());credential(user.id(),canonical,hash);
            audit.record(actor.actor(),user.tenantId(),"USER_CREATED","USER",user.id(),Map.of("scope","GLOBAL","role","USER","status","ACTIVE"));return user;
        });
    }
    private UUID administrativeScope(Identity actor){
        Authorization.administrator(actor);return actor.globalAdmin()?null:actor.tenantId();
    }
    public com.github.ksewen.kelios.platform.Page<com.github.ksewen.kelios.identity.UserModel> list(Identity actor,com.github.ksewen.kelios.identity.UserQuery query){
        UUID forced=administrativeScope(actor);
        // A TENANT principal has no transport Tenant criterion in its effective query.
        var effective=forced==null?query:new com.github.ksewen.kelios.identity.UserQuery(query.page(),query.size(),query.search(),query.scope(),query.role(),query.status(),null,query.sort(),query.direction());
        return tx.execute(s->{
            if(forced==null && effective.tenantId()!=null && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM tenant WHERE id=?)",Boolean.class,effective.tenantId())))throw new AuthFailure(404,"Tenant not found");
            return users.list(forced,effective);
        });
    }
    public com.github.ksewen.kelios.identity.UserModel detail(Identity actor,UUID id){UUID forced=administrativeScope(actor);return tx.execute(s->{
        var u=users.detail(id,forced);
        com.github.ksewen.kelios.identity.UserModel.GlobalAccount account=null;
        if(u.scope()==Scope.GLOBAL){var rows=jdbc.query("SELECT email,email_verified_at FROM local_global_credential WHERE user_id=?",(r,n)->new com.github.ksewen.kelios.identity.UserModel.GlobalAccount(r.getString("email"),r.getTimestamp("email_verified_at")==null?null:r.getTimestamp("email_verified_at").toInstant()),id);if(!rows.isEmpty())account=rows.getFirst();}
        return new com.github.ksewen.kelios.identity.UserModel(u.id(),u.scope(),u.role(),u.status(),u.displayName(),u.tenant(),u.metadata(),account);
    });}
    public Identity approve(Identity actor,UUID target){
        Authorization.administrator(actor);
        return tx.execute(s->{
            // Only GLOBAL ADMIN approval can make a legacy GLOBAL ADMIN usable.
            Identity initial=users.managed(actor,target,"");
            boolean guarded=initial.scope()==Scope.GLOBAL && initial.role()==Role.ADMIN;
            if(guarded)users.recoveryGuard();
            Identity user=users.managed(actor,target," FOR UPDATE");Authorization.manageUser(actor,user);
            if(!guarded && user.scope()==Scope.GLOBAL && user.role()==Role.ADMIN)throw new AuthFailure(409,"User role changed concurrently");
            if(user.status()!=Status.PENDING)throw new AuthFailure(409,"User is not pending");
            jdbc.update("UPDATE app_user SET status='ACTIVE' WHERE id=?",target);
            users.touch(target,actor.actor());
            audit.record(actor.actor(),user.tenantId(),"USER_APPROVED","USER",target,Map.of("before","PENDING","after","ACTIVE"));return users.load(target,"");
        });
    }
    public Identity role(Identity actor,UUID target,Role role){
        Authorization.globalAdmin(actor);if(role==null)throw new AuthFailure(400,"Role required");
        if(role!=Role.ADMIN)Authorization.destructiveSelf(actor,target);
        return tx.execute(s->{
            if(role==Role.ADMIN && users.load(target,"").scope()==Scope.GLOBAL)users.recoveryGuard();
            Identity user=users.load(target," FOR UPDATE");if(user.role()==role)throw new AuthFailure(409,"Role unchanged");
            jdbc.update("UPDATE app_user SET role=? WHERE id=?",role.name(),target);
            users.touch(target,actor.actor());
            audit.record(actor.actor(),user.tenantId(),"USER_ROLE_CHANGED","USER",target,Map.of("before",user.role().name(),"after",role.name()));return users.load(target,"");
        });
    }
    public Identity disable(Identity actor,UUID target){
        Authorization.administrator(actor);if(actor.globalAdmin())Authorization.destructiveSelf(actor,target);
        return tx.execute(s->{
            Identity user=users.managed(actor,target," FOR UPDATE");Authorization.manageUser(actor,user);
            if(user.status()==Status.DISABLED)throw new AuthFailure(409,"User already disabled");
            jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=?",target);
            users.touch(target,actor.actor());
            audit.record(actor.actor(),user.tenantId(),"USER_DISABLED","USER",target,Map.of("before",user.status().name(),"after","DISABLED"));return users.load(target,"");
        });
    }
    public void resetCredential(Identity actor,UUID target,String email,String password){
        Authorization.globalAdmin(actor);String canonical=passwords.email(email),hash=passwords.hash(password);
        tx.executeWithoutResult(s->{
            // Repairing a malformed/missing ADMIN credential can create usability; serialize with recovery.
            Identity initial=users.load(target,"");if(initial.scope()!=Scope.GLOBAL)throw AuthFailure.denied();
            boolean guarded=initial.role()==Role.ADMIN && initial.status()==Status.ACTIVE;
            if(guarded)users.recoveryGuard();
            Identity user=users.load(target," FOR UPDATE");if(user.scope()!=Scope.GLOBAL)throw AuthFailure.denied();
            if(!guarded && user.role()==Role.ADMIN && user.status()==Status.ACTIVE)throw new AuthFailure(409,"User state changed concurrently");
            jdbc.update("INSERT INTO local_global_credential(user_id,email,password_hash,email_verified_at) VALUES(?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET email=EXCLUDED.email,password_hash=EXCLUDED.password_hash,email_verified_at=EXCLUDED.email_verified_at",target,canonical,hash,java.sql.Timestamp.from(clock.instant()));
            users.touch(target,actor.actor());
            audit.record(actor.actor(),user.tenantId(),"LOCAL_CREDENTIAL_CHANGED","USER",target,Map.of("operation","administrator-reset"));
        });
    }
    private boolean usableCredential(Map<String,Object> credential){
        if(credential.get("email_verified_at")==null || !passwords.usable((String)credential.get("password_hash")))return false;
        try{return passwords.email((String)credential.get("email")).equals(credential.get("email"));}catch(AuthFailure ex){return false;}
    }
    private boolean verifiedCredential(UUID id){
        var rows=jdbc.queryForList("SELECT email,password_hash,email_verified_at FROM local_global_credential WHERE user_id=? FOR SHARE",id);
        return !rows.isEmpty() && usableCredential(rows.getFirst());
    }
    public void email(Identity actor,UUID target,String value){
        Authorization.globalAdmin(actor);String canonical=passwords.email(value);
        tx.executeWithoutResult(s->{
            Identity initial=users.load(target,"");if(initial.scope()!=Scope.GLOBAL)throw AuthFailure.denied();
            boolean guarded=initial.role()==Role.ADMIN && initial.status()==Status.ACTIVE;
            if(guarded)users.recoveryGuard();
            Identity user=users.load(target," FOR UPDATE");if(user.scope()!=Scope.GLOBAL)throw AuthFailure.denied();
            if(!guarded && user.role()==Role.ADMIN && user.status()==Status.ACTIVE)throw new AuthFailure(409,"User state changed concurrently");
            if(jdbc.update("UPDATE local_global_credential SET email=?,email_verified_at=? WHERE user_id=?",canonical,java.sql.Timestamp.from(clock.instant()),target)!=1)throw new AuthFailure(404,"Credential not found");
            users.touch(target,actor.actor());
            audit.record(actor.actor(),null,"GLOBAL_EMAIL_CHANGED","USER",target,Map.of("operation","administrator-email-change"));
        });
    }
    public void ownPassword(Identity actor,String current,String replacement){
        Authorization.active(actor);if(actor.scope()!=Scope.GLOBAL)throw AuthFailure.denied();
        var hashes=jdbc.queryForList("SELECT password_hash FROM local_global_credential WHERE user_id=?",String.class,actor.id());
        String old=hashes.isEmpty()?null:hashes.getFirst();if(!passwords.matches(current,old))throw AuthFailure.invalid();String hash=passwords.hash(replacement);
        tx.executeWithoutResult(s->{
            Identity user=users.load(actor.id()," FOR UPDATE");if(user.scope()!=Scope.GLOBAL)throw AuthFailure.denied();
            int updated=jdbc.update("UPDATE local_global_credential SET password_hash=? WHERE user_id=? AND password_hash=?",hash,actor.id(),old);
            if(updated!=1)throw new AuthFailure(409,"Credential changed concurrently");
            users.touch(actor.id(),actor.actor());
            audit.record(actor.actor(),user.tenantId(),"LOCAL_CREDENTIAL_CHANGED","USER",actor.id(),Map.of("operation","own-password-change"));
        });
    }
}
