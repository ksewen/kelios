package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.audit.AuditService;
import com.github.ksewen.kelios.identity.UserService.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OidcProvisioning {
    private final JdbcTemplate jdbc;private final OidcConfigurationService configs;private final IdentityStore users;private final AuditService audit;private final JwtTokens tokens;
    public OidcProvisioning(JdbcTemplate jdbc,OidcConfigurationService configs,IdentityStore users,AuditService audit,JwtTokens tokens){this.jdbc=jdbc;this.configs=configs;this.users=users;this.audit=audit;this.tokens=tokens;}
    /** Internal verified protocol boundary; never exposed as a request-body identity endpoint. */
    @Transactional public JwtTokens.Pair complete(UUID tenant,long version,String issuer,String subject,String displayName,Set<UUID> verifiedGroups){
        var provider=configs.locked(tenant);
        if(!provider.enabled() || provider.version()!=version || !provider.issuer().equals(issuer))throw new AuthFailure(409,"Provider configuration changed during login");
        IServGroups.requireAllowed(verifiedGroups,provider.allowedGroupIds());
        if(subject==null || subject.isBlank() || subject.length()>2048)throw AuthFailure.denied();
        String display=displayName==null || displayName.isBlank()?subject:displayName;
        if(display.length()>256)throw AuthFailure.denied();
        UUID candidate=UUID.randomUUID();
        var inserted=jdbc.query("INSERT INTO external_identity(issuer,subject,user_id,tenant_id) VALUES(?,?,?,?) ON CONFLICT(issuer,subject) DO NOTHING RETURNING user_id",(rs,n)->rs.getObject(1,UUID.class),issuer,subject,candidate,tenant);
        UUID id;
        if(!inserted.isEmpty()){
            var created=new Identity(candidate,Scope.TENANT,tenant,Role.USER,Status.PENDING,display);users.insert(created);
            audit.record(created.actor(),created.tenantId(),"USER_CREATED","USER",created.id(),Map.of("scope","TENANT","role","USER","status","PENDING","origin","OIDC"));id=candidate;
        }else {
            var mapping=jdbc.queryForMap("SELECT user_id,tenant_id FROM external_identity WHERE issuer=? AND subject=?",issuer,subject);
            if(!tenant.equals(mapping.get("tenant_id")))throw AuthFailure.denied();id=(UUID)mapping.get("user_id");
        }
        var user=users.load(id," FOR SHARE");if(user.scope()!=Scope.TENANT || !tenant.equals(user.tenantId()) || user.status()==Status.DISABLED)throw AuthFailure.denied();
        return tokens.login(user);
    }
}
