package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.audit.AuditService;
import com.github.ksewen.kelios.tenancy.TenantService;
import java.net.URI;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class OidcConfigurationService {
    /** Secret never appears in a configuration response or record toString. */
    public record View(UUID tenantId,String issuer,String clientId,boolean secretConfigured,boolean enabled,long version,Set<UUID> allowedGroupIds,String callbackUri) {}
    public record Provider(UUID tenantId,String issuer,String clientId,String encryptedSecret,boolean enabled,long version,Set<UUID> allowedGroupIds) {
        @Override public String toString(){return "Provider[tenant="+tenantId+",version="+version+"]";}
    }
    /** Public routing directory: no identity facts, issuer, credentials or provider policy. */
    public record Organization(UUID routingId,String name) {}
    public record Organizations(List<Organization> items,long totalElements,int page,int size) {}
    @Transactional(readOnly=true) public Organizations organizations(int page,int size,String q){
        if(page<0 || page>100000 || size<1 || size>100 || q==null || q.codePointCount(0,q.length())>128)throw new AuthFailure(400,"Invalid organization query");
        String pattern="%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        String from=" FROM tenant t JOIN tenant_oidc_config p ON p.tenant_id=t.id WHERE p.enabled AND t.name ILIKE ? ESCAPE E'\\\\'";
        long total=jdbc.queryForObject("SELECT count(*)"+from,Long.class,pattern);
        var rows=jdbc.query("SELECT t.id,t.name"+from+" ORDER BY lower(t.name),t.id LIMIT ? OFFSET ?",
            (rs,n)->new Organization(rs.getObject("id",UUID.class),rs.getString("name")),pattern,size,(long)page*size);
        return new Organizations(rows,total,page,size);
    }
    private final JdbcTemplate jdbc;private final TenantService tenants;private final SecretEnvelope secrets;private final AuthSettings settings;private final AuditService audit;
    public OidcConfigurationService(JdbcTemplate jdbc,TenantService tenants,SecretEnvelope secrets,AuthSettings settings,AuditService audit){this.jdbc=jdbc;this.tenants=tenants;this.secrets=secrets;this.settings=settings;this.audit=audit;}
    public static String context(UUID tenant,String issuer,String client){return "oidc-secret:"+tenant+":"+issuer+":"+client;}
    private String text(String value){if(value==null || value.isBlank() || value.length()>2048)throw new AuthFailure(400,"Invalid provider configuration");return value;}
    public static URI issuerUri(String issuer){
        try{
            URI u=URI.create(issuer);
            if(!"https".equals(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null)throw new IllegalArgumentException();return u;
        }catch(RuntimeException ex){throw new AuthFailure(400,"Issuer must be a valid HTTPS URI");}
    }
    @Transactional(readOnly=true) public View get(Identity actor,UUID tenant){Authorization.globalAdmin(actor);return view(load(tenant,""));}
    @Transactional public View put(Identity actor,UUID tenant,String issuer,String client,String secret,boolean enabled,Long expectedVersion,Set<UUID> groups){
        Authorization.globalAdmin(actor);tenants.require(tenant);text(issuer);issuerUri(issuer);text(client);
        if(groups==null || groups.size()>256 || groups.stream().anyMatch(Objects::isNull))throw new AuthFailure(400,"Allowed group UUIDs required");
        // A Tenant parent lock also serializes the first configuration insert.
        lockTenant(tenant);
        Provider old=null;try{old=load(tenant," FOR UPDATE");}catch(AuthFailure ex){if(ex.status()!=404)throw ex;}
        if((old==null && expectedVersion!=null) || (old!=null && !Objects.equals(expectedVersion,old.version())))throw new AuthFailure(409,"Provider configuration version conflict");
        String encrypted;
        if(secret!=null){text(secret);encrypted=secrets.encrypt(secret,context(tenant,issuer,client));}
        else {if(old==null)throw new AuthFailure(400,"Initial client secret required");String plaintext=secrets.decrypt(old.encryptedSecret(),context(tenant,old.issuer(),old.clientId()));encrypted=secrets.encrypt(plaintext,context(tenant,issuer,client));}
        long version=old==null?0:Math.addExact(old.version(),1);
        jdbc.update("INSERT INTO tenant_oidc_config(tenant_id,issuer,client_id,encrypted_client_secret,enabled,version) VALUES(?,?,?,?,?,?) ON CONFLICT(tenant_id) DO UPDATE SET issuer=EXCLUDED.issuer,client_id=EXCLUDED.client_id,encrypted_client_secret=EXCLUDED.encrypted_client_secret,enabled=EXCLUDED.enabled,version=EXCLUDED.version",tenant,issuer,client,encrypted,enabled,version);
        jdbc.update("DELETE FROM tenant_oidc_allowed_group WHERE tenant_id=?",tenant);for(UUID group:groups)jdbc.update("INSERT INTO tenant_oidc_allowed_group(tenant_id,group_id) VALUES(?,?)",tenant,group);
        audit.record(actor.actor(),tenant,"OIDC_CONFIG_CHANGED","TENANT",tenant,Map.of("version",version,"enabled",enabled,"allowedGroupsChanged",true));return view(load(tenant,""));
    }
    private void lockTenant(UUID tenant){
        // Lock is exposed by the Tenant application interface, never another module's repository.
        tenants.lock(tenant);
    }
    private View view(Provider p){return new View(p.tenantId(),p.issuer(),p.clientId(),true,p.enabled(),p.version(),p.allowedGroupIds(),settings.callback(p.tenantId()));}
    @Transactional(readOnly=true) public Provider provider(UUID tenant){Provider p=load(tenant,"");if(!p.enabled())throw AuthFailure.denied();return p;}
    @Transactional(propagation=Propagation.MANDATORY) public Provider locked(UUID tenant){return load(tenant," FOR SHARE");}
    private Provider load(UUID tenant,String lock){
        var rows=jdbc.query("SELECT * FROM tenant_oidc_config WHERE tenant_id=?"+lock,(rs,n)->new Provider(tenant,rs.getString("issuer"),rs.getString("client_id"),rs.getString("encrypted_client_secret"),rs.getBoolean("enabled"),rs.getLong("version"),Set.of()),tenant);
        if(rows.isEmpty())throw new AuthFailure(404,"Tenant identity provider not configured");Provider p=rows.getFirst();
        var groups=Set.copyOf(jdbc.queryForList("SELECT group_id FROM tenant_oidc_allowed_group WHERE tenant_id=?",UUID.class,tenant));
        return new Provider(tenant,p.issuer(),p.clientId(),p.encryptedSecret(),p.enabled(),p.version(),groups);
    }
    public String secret(Provider p){return secrets.decrypt(p.encryptedSecret(),context(p.tenantId(),p.issuer(),p.clientId()));}
}
