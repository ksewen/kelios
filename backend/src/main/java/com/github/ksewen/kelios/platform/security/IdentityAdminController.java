package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.auth.*;
import com.github.ksewen.kelios.identity.UserService.Role;
import com.github.ksewen.kelios.tenancy.TenantService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class IdentityAdminController {
    public record TenantInput(String name){}
    public record GlobalInput(String login,String displayName,String password){@Override public String toString(){return "GlobalInput[redacted]";}}
    public record RoleInput(Role role){}
    public record CredentialInput(String login,String password){@Override public String toString(){return "CredentialInput[redacted]";}}
    public record ProviderInput(String issuer,String clientId,String clientSecret,boolean enabled,Long version,Set<UUID> allowedGroupIds){@Override public String toString(){return "ProviderInput[redacted]";}}
    private final AuthenticationService users;private final TenantService tenants;private final OidcConfigurationService providers;
    public IdentityAdminController(AuthenticationService users,TenantService tenants,OidcConfigurationService providers){this.users=users;this.tenants=tenants;this.providers=providers;}
    @GetMapping("/tenants") public List<TenantService.View> tenants(){var actor=CurrentIdentity.require();Authorization.active(actor);return tenants.list(actor.scope()==com.github.ksewen.kelios.identity.UserService.Scope.GLOBAL?null:actor.tenantId());}
    @PostMapping("/tenants") public UUID tenant(@RequestBody TenantInput command){var actor=CurrentIdentity.require();Authorization.globalAdmin(actor);return tenants.create(command.name(),actor.actor());}
    @GetMapping("/users") public List<Identity> users(){return users.list(CurrentIdentity.require());}
    @PostMapping("/users/global") public Identity global(@RequestBody GlobalInput command){return users.createGlobal(CurrentIdentity.require(),command.login(),command.displayName(),command.password());}
    @PostMapping("/users/{id}/approve") public Identity approve(@PathVariable UUID id){return users.approve(CurrentIdentity.require(),id);}
    @PutMapping("/users/{id}/role") public Identity role(@PathVariable UUID id,@RequestBody RoleInput command){return users.role(CurrentIdentity.require(),id,command.role());}
    @PostMapping("/users/{id}/disable") public Identity disable(@PathVariable UUID id){return users.disable(CurrentIdentity.require(),id);}
    @PutMapping("/users/{id}/local-credential") public void credential(@PathVariable UUID id,@RequestBody CredentialInput command){users.resetCredential(CurrentIdentity.require(),id,command.login(),command.password());}
    @GetMapping("/tenants/{id}/oidc") public OidcConfigurationService.View provider(@PathVariable UUID id){return providers.get(CurrentIdentity.require(),id);}
    @PutMapping("/tenants/{id}/oidc") public OidcConfigurationService.View provider(@PathVariable UUID id,@RequestBody ProviderInput command){return providers.put(CurrentIdentity.require(),id,command.issuer(),command.clientId(),command.clientSecret(),command.enabled(),command.version(),command.allowedGroupIds());}
}
