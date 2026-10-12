package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.auth.*;
import com.github.ksewen.kelios.identity.UserService.Role;
import com.github.ksewen.kelios.tenancy.TenantOperations;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class IdentityAdminController {
    private final AuthenticationService users;private final TenantOperations tenants;private final OidcConfigurationService providers;
    public IdentityAdminController(AuthenticationService users,TenantOperations tenants,OidcConfigurationService providers){this.users=users;this.tenants=tenants;this.providers=providers;}
    @GetMapping("/tenants") public List<TenantDtos.Item> tenants(@RequestParam Map<String,String> query){TenantDtos.query(query);return tenants.list().stream().map(TenantDtos::item).toList();}
    @GetMapping("/tenants/{id}") public TenantDtos.Detail tenant(@PathVariable UUID id){return TenantDtos.detail(tenants.detail(id));}
    @PostMapping("/tenants") public UUID tenant(@RequestBody TenantDtos.Create command){return tenants.create(command.name());}
    @GetMapping("/users") public com.github.ksewen.kelios.platform.Page<UserDtos.Item> users(@RequestParam Map<String,String> q){return UserDtos.page(users.list(CurrentIdentity.require(),UserDtos.query(q)));}
    @GetMapping("/users/{id}") public UserDtos.Detail user(@PathVariable UUID id){return UserDtos.detail(users.detail(CurrentIdentity.require(),id));}
    @PostMapping("/users/global") public UserDtos.State global(@RequestBody UserDtos.GlobalInput command){return UserDtos.state(users.createGlobal(CurrentIdentity.require(),command.email(),command.displayName(),command.password()));}
    @PostMapping("/users/{id}/approve") public UserDtos.State approve(@PathVariable UUID id){return UserDtos.state(users.approve(CurrentIdentity.require(),id));}
    @PutMapping("/users/{id}/role") public UserDtos.State role(@PathVariable UUID id,@RequestBody UserDtos.RoleInput command){return UserDtos.state(users.role(CurrentIdentity.require(),id,command.role()));}
    @PostMapping("/users/{id}/disable") public UserDtos.State disable(@PathVariable UUID id){return UserDtos.state(users.disable(CurrentIdentity.require(),id));}
    @PutMapping("/users/{id}/local-credential") public void credential(@PathVariable UUID id,@RequestBody UserDtos.CredentialInput command){users.resetCredential(CurrentIdentity.require(),id,command.email(),command.password());}
    @PutMapping("/users/{id}/email") public void email(@PathVariable UUID id,@RequestBody UserDtos.EmailInput command){users.email(CurrentIdentity.require(),id,command.email());}
    @GetMapping("/tenants/{id}/oidc") public OidcConfigurationDtos.Detail provider(@PathVariable UUID id){return OidcConfigurationDtos.detail(providers.get(CurrentIdentity.require(),id));}
    @DeleteMapping("/tenants/{id}/oidc") public void deleteProvider(@PathVariable UUID id,@RequestParam Map<String,String> query){providers.delete(CurrentIdentity.require(),id,OidcConfigurationDtos.deleteVersion(query));}
    @PutMapping("/tenants/{id}/oidc") public OidcConfigurationDtos.Detail provider(@PathVariable UUID id,@RequestBody OidcConfigurationDtos.Configure command){return OidcConfigurationDtos.detail(providers.put(CurrentIdentity.require(),id,command.issuer(),command.clientId(),command.clientSecret(),command.enabled(),command.version(),command.allowedGroupIds()));}
}
