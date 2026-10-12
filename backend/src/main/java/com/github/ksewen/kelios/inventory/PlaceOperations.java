package com.github.ksewen.kelios.inventory;

import java.util.UUID;
import com.github.ksewen.kelios.identity.UserService.Scope;
import com.github.ksewen.kelios.identity.auth.Authorization;
import com.github.ksewen.kelios.identity.auth.Identity;
import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.platform.Page;
import com.github.ksewen.kelios.platform.security.CurrentIdentity;
import com.github.ksewen.kelios.tenancy.TenantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant authority and orchestration; trusted Place writers retain distinct Location/Owner guards. */
@Service
public class PlaceOperations {
    private final PlaceReads reads;private final PlaceService places;private final TenantService tenants;
    public PlaceOperations(PlaceReads reads,PlaceService places,TenantService tenants){this.reads=reads;this.places=places;this.tenants=tenants;}
    private Identity actor(){var a=CurrentIdentity.require();Authorization.active(a);return a;}
    private UUID context(Identity a,UUID supplied){
        UUID effective=a.scope()==Scope.TENANT?a.tenantId():supplied;
        Authorization.readTenant(a,effective);
        try{tenants.require(effective);}catch(IllegalArgumentException ex){throw BusinessFailure.missing();}
        return effective;
    }
    @Transactional(readOnly=true) public Page<PlaceModel> list(PlaceService.Kind kind,UUID supplied,PlaceQuery query){var a=actor();return reads.list(kind,context(a,supplied),query);}
    @Transactional(readOnly=true) public PlaceModel detail(PlaceService.Kind kind,UUID id){var a=actor();return reads.detail(kind,id,a.scope()==Scope.TENANT?a.tenantId():null);}
    @Transactional public UUID create(PlaceService.Kind kind,UUID supplied,PlaceModel.Command command){
        var a=actor();Authorization.administrator(a);UUID tenant=context(a,supplied);Authorization.writeTenant(a,tenant);
        return places.create(kind,tenant,command.name(),command.note(),a.actor());
    }
    @Transactional public void edit(PlaceService.Kind kind,UUID id,PlaceModel.Command command){
        var a=actor();Authorization.administrator(a);var target=reads.detail(kind,id,a.scope()==Scope.TENANT?a.tenantId():null);Authorization.writeTenant(a,target.tenant().id());
        places.edit(kind,target.tenant().id(),id,command.name(),command.note(),a.actor());
    }
    @Transactional public void delete(PlaceService.Kind kind,UUID id){
        var a=actor();Authorization.administrator(a);var target=reads.detail(kind,id,a.scope()==Scope.TENANT?a.tenantId():null);Authorization.writeTenant(a,target.tenant().id());places.delete(kind,target.tenant().id(),id,a.actor());
    }
}
