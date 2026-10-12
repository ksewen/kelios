package com.github.ksewen.kelios.tenancy;

import com.github.ksewen.kelios.identity.auth.*;
import com.github.ksewen.kelios.platform.security.CurrentIdentity;
import com.github.ksewen.kelios.identity.UserService.Scope;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application authority; a resource UUID locates a Tenant but grants no access. */
@Service
public class TenantOperations {
    private final TenantService tenants;private final TenantReads reads;
    public TenantOperations(TenantService tenants,TenantReads reads){this.tenants=tenants;this.reads=reads;}
    private Identity reader(){var actor=CurrentIdentity.require();Authorization.active(actor);if(actor.scope()==Scope.TENANT)Authorization.administrator(actor);return actor;}
    private UUID scope(Identity actor){return actor.scope()==Scope.TENANT?actor.tenantId():null;}
    @Transactional(readOnly=true) public List<TenantModel> list(){var actor=reader();if(actor.scope()!=Scope.GLOBAL)throw AuthFailure.denied();return reads.list(null);}
    @Transactional(readOnly=true) public TenantModel detail(UUID id){var actor=reader();return reads.detail(id,scope(actor));}
    @Transactional public UUID create(String name){var actor=CurrentIdentity.require();Authorization.globalAdmin(actor);return tenants.create(name,actor.actor());}
}
