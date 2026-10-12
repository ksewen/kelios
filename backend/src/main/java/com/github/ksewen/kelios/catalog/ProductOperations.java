package com.github.ksewen.kelios.catalog;

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

/** Product service authority; existing trusted writers own lifecycle/reference guards and atomic audit. */
@Service
public class ProductOperations {
    private final ProductReads reads;private final CatalogService catalog;private final TenantService tenants;
    public ProductOperations(ProductReads reads,CatalogService catalog,TenantService tenants){this.reads=reads;this.catalog=catalog;this.tenants=tenants;}
    private Identity actor(){var a=CurrentIdentity.require();Authorization.active(a);return a;}
    private UUID context(Identity a,UUID supplied){
        UUID effective=a.scope()==Scope.TENANT?a.tenantId():supplied;
        if(effective!=null)try{tenants.require(effective);}catch(IllegalArgumentException ex){throw BusinessFailure.missing();}
        return effective;
    }
    @Transactional(readOnly=true) public Page<ProductModel> list(UUID supplied,ProductQuery query){var a=actor();return reads.list(context(a,supplied),query);}
    @Transactional(readOnly=true) public ProductModel detail(UUID id){var a=actor();return reads.detail(id,a.scope()==Scope.TENANT?a.tenantId():null);}
    @Transactional public UUID create(UUID supplied,ProductModel.Command command){
        var a=actor();Authorization.administrator(a);UUID tenant=context(a,supplied);Authorization.writeCatalog(a,tenant);
        return catalog.createProduct(tenant,command.categoryId(),command.manufacturerId(),command.name(),command.manufacturerNumber(),a.actor());
    }
    private Identity writable(UUID id,boolean lock){var a=actor();Authorization.administrator(a);var target=catalog.scoped(CatalogService.Kind.PRODUCT,id,a.scope()==Scope.TENANT?a.tenantId():null,lock);Authorization.writeCatalog(a,target.tenantId());return a;}
    @Transactional public void edit(UUID id,ProductModel.Command command){var a=writable(id,false);catalog.editProduct(id,command.categoryId(),command.manufacturerId(),command.name(),command.manufacturerNumber(),a.actor(),a.scope()==Scope.TENANT?a.tenantId():null);}
    @Transactional public void delete(UUID id){var a=writable(id,true);catalog.delete(CatalogService.Kind.PRODUCT,id,a.actor());}
    @Transactional public void publish(UUID id){var a=actor();Authorization.globalAdmin(a);catalog.publish(CatalogService.Kind.PRODUCT,id,a.actor());}
}
