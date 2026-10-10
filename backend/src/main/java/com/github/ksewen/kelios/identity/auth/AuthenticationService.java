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
    private final AuditService audit;private final JwtTokens tokens;private final TransactionTemplate tx;
    public AuthenticationService(JdbcTemplate jdbc,IdentityStore users,Passwords passwords,AuditService audit,JwtTokens tokens,PlatformTransactionManager tm){
        this.jdbc=jdbc;this.users=users;this.passwords=passwords;this.audit=audit;this.tokens=tokens;this.tx=new TransactionTemplate(tm);
    }
    private String name(String value){if(value==null || value.isBlank() || value.length()>256)throw new AuthFailure(400,"Display name required");return value.strip();}
    private void credential(UUID user,String login,String hash){jdbc.update("INSERT INTO local_global_credential(user_id,login_name,password_hash) VALUES(?,?,?)",user,login,hash);}
    public Identity bootstrap(String login,String displayName,String password){
        String canonical=passwords.login(login),display=name(displayName),hash=passwords.hash(password);
        return tx.execute(s->{
            users.recoveryGuard();
            var hashes=jdbc.queryForList("SELECT c.password_hash FROM app_user u JOIN local_global_credential c ON c.user_id=u.id WHERE u.scope='GLOBAL' AND u.role='ADMIN' AND u.status='ACTIVE'",String.class);
            if(hashes.stream().anyMatch(passwords::usable))throw new AuthFailure(409,"A usable GLOBAL administrator already exists");
            var user=new Identity(UUID.randomUUID(),Scope.GLOBAL,null,Role.ADMIN,Status.ACTIVE,display);
            users.insert(user);credential(user.id(),canonical,hash);
            audit.record(user.actor(),user.tenantId(),"GLOBAL_ADMIN_BOOTSTRAPPED","USER",user.id(),Map.of("scope","GLOBAL"));return user;
        });
    }
    public JwtTokens.Pair login(String login,String password){
        String canonical;
        try{canonical=passwords.login(login);}catch(AuthFailure ex){passwords.matches(password,null);throw AuthFailure.invalid();}
        var rows=jdbc.queryForList("SELECT user_id,password_hash FROM local_global_credential WHERE login_name=?",canonical);
        String hash=rows.isEmpty()?null:(String)rows.getFirst().get("password_hash");
        if(!passwords.matches(password,hash))throw AuthFailure.invalid();
        UUID id=(UUID)rows.getFirst().get("user_id");
        return tx.execute(s->{
            Identity user=users.load(id," FOR SHARE");
            var current=jdbc.queryForList("SELECT password_hash FROM local_global_credential WHERE user_id=? FOR SHARE",String.class,id);
            if(user.scope()!=Scope.GLOBAL || current.isEmpty() || !hash.equals(current.getFirst()))throw AuthFailure.invalid();
            if(user.status()==Status.DISABLED)throw AuthFailure.denied();return tokens.login(user);
        });
    }
    public JwtTokens.Pair refresh(String refresh){
        var verified=tokens.verify(refresh,"refresh");
        return tx.execute(s->{
            Identity user;
            try{user=users.load(verified.userId()," FOR SHARE");}catch(AuthFailure ex){throw AuthFailure.invalid();}
            if(user.status()==Status.DISABLED)throw AuthFailure.denied();return tokens.refresh(user,refresh);
        });
    }
    public Identity createGlobal(Identity actor,String login,String displayName,String password){
        Authorization.globalAdmin(actor);String canonical=passwords.login(login),display=name(displayName),hash=passwords.hash(password);
        return tx.execute(s->{
            var user=new Identity(UUID.randomUUID(),Scope.GLOBAL,null,Role.USER,Status.ACTIVE,display);users.insert(user);credential(user.id(),canonical,hash);
            audit.record(actor.actor(),user.tenantId(),"USER_CREATED","USER",user.id(),Map.of("scope","GLOBAL","role","USER","status","ACTIVE"));return user;
        });
    }
    public List<Identity> list(Identity actor){
        Authorization.active(actor);if(!actor.globalAdmin() && !(actor.scope()==Scope.TENANT && actor.role()==Role.ADMIN))throw AuthFailure.denied();
        return users.list(actor.scope()==Scope.GLOBAL?null:actor.tenantId());
    }
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
            audit.record(actor.actor(),user.tenantId(),"USER_ROLE_CHANGED","USER",target,Map.of("before",user.role().name(),"after",role.name()));return users.load(target,"");
        });
    }
    public Identity disable(Identity actor,UUID target){
        Authorization.administrator(actor);if(actor.globalAdmin())Authorization.destructiveSelf(actor,target);
        return tx.execute(s->{
            Identity user=users.managed(actor,target," FOR UPDATE");Authorization.manageUser(actor,user);
            if(user.status()==Status.DISABLED)throw new AuthFailure(409,"User already disabled");
            jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=?",target);
            audit.record(actor.actor(),user.tenantId(),"USER_DISABLED","USER",target,Map.of("before",user.status().name(),"after","DISABLED"));return users.load(target,"");
        });
    }
    public void resetCredential(Identity actor,UUID target,String login,String password){
        Authorization.globalAdmin(actor);String canonical=passwords.login(login),hash=passwords.hash(password);
        tx.executeWithoutResult(s->{
            // Repairing a malformed/missing ADMIN credential can create usability; serialize with recovery.
            Identity initial=users.load(target,"");if(initial.scope()!=Scope.GLOBAL)throw AuthFailure.denied();
            boolean guarded=initial.role()==Role.ADMIN && initial.status()==Status.ACTIVE;
            if(guarded)users.recoveryGuard();
            Identity user=users.load(target," FOR UPDATE");if(user.scope()!=Scope.GLOBAL)throw AuthFailure.denied();
            if(!guarded && user.role()==Role.ADMIN && user.status()==Status.ACTIVE)throw new AuthFailure(409,"User state changed concurrently");
            jdbc.update("INSERT INTO local_global_credential(user_id,login_name,password_hash) VALUES(?,?,?) ON CONFLICT(user_id) DO UPDATE SET login_name=EXCLUDED.login_name,password_hash=EXCLUDED.password_hash",target,canonical,hash);
            audit.record(actor.actor(),user.tenantId(),"LOCAL_CREDENTIAL_CHANGED","USER",target,Map.of("operation","administrator-reset"));
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
            audit.record(actor.actor(),user.tenantId(),"LOCAL_CREDENTIAL_CHANGED","USER",actor.id(),Map.of("operation","own-password-change"));
        });
    }
}
