package com.github.ksewen.kelios.catalog;

import com.github.ksewen.kelios.identity.UserService.Scope;
import com.github.ksewen.kelios.identity.auth.Authorization;
import com.github.ksewen.kelios.identity.auth.Identity;
import com.github.ksewen.kelios.inventory.CatalogMergeService;
import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.platform.Page;
import com.github.ksewen.kelios.platform.security.CurrentIdentity;
import com.github.ksewen.kelios.tenancy.TenantService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoritative Category/Manufacturer scope and operations; trusted writers retain existing guards/transactions. */
@Service
public class CatalogLabelOperations {
    private final CatalogLabelReads reads;
    private final CatalogService catalog;
    private final CatalogMergeService merges;
    private final TenantService tenants;
    public CatalogLabelOperations(CatalogLabelReads reads, CatalogService catalog, CatalogMergeService merges, TenantService tenants) {
        this.reads = reads; this.catalog = catalog; this.merges = merges; this.tenants = tenants;
    }
    private Identity actor() { var a = CurrentIdentity.require(); Authorization.active(a); return a; }
    private UUID context(Identity actor, UUID suppliedTenant) {
        // TENANT supplied IDs are neither used nor compared as authority, including foreign/nonexistent path IDs.
        UUID effective = actor.scope() == Scope.TENANT ? actor.tenantId() : suppliedTenant;
        if (effective != null) {
            try { tenants.require(effective); } catch (IllegalArgumentException ex) { throw BusinessFailure.missing(); }
        }
        return effective;
    }
    @Transactional(readOnly = true)
    public Page<CatalogLabel> list(CatalogLabel.Kind kind, UUID suppliedTenant, CatalogLabelQuery query) {
        var a = actor(); return reads.list(kind, context(a, suppliedTenant), query);
    }
    @Transactional(readOnly = true)
    public CatalogLabel detail(CatalogLabel.Kind kind, UUID id) {
        var a = actor(); return reads.detail(kind, id, a.scope() == Scope.TENANT ? a.tenantId() : null);
    }
    @Transactional
    public UUID create(CatalogLabel.Kind kind, UUID suppliedTenant, CatalogLabel.Name name) {
        var a = actor(); Authorization.administrator(a);
        UUID tenant = context(a, suppliedTenant); Authorization.writeCatalog(a, tenant);
        return kind == CatalogLabel.Kind.CATEGORY ? catalog.createCategory(tenant, name.value(), a.actor())
            : catalog.createManufacturer(tenant, name.value(), a.actor());
    }
    private Identity writable(CatalogLabel.Kind kind, UUID id) {
        var a = actor(); Authorization.administrator(a);
        var target = catalog.scoped(kind.legacyKind(), id, a.scope() == Scope.TENANT ? a.tenantId() : null, true);
        Authorization.writeCatalog(a, target.tenantId()); return a;
    }
    @Transactional
    public void edit(CatalogLabel.Kind kind, UUID id, CatalogLabel.Name name) {
        var a = writable(kind, id); catalog.editLabel(kind.legacyKind(), id, name.value(), a.actor());
    }
    @Transactional
    public void delete(CatalogLabel.Kind kind, UUID id) {
        var a = writable(kind, id); catalog.delete(kind.legacyKind(), id, a.actor());
    }
    @Transactional
    public void publish(CatalogLabel.Kind kind, UUID id) {
        var a = actor(); Authorization.globalAdmin(a); catalog.publish(kind.legacyKind(), id, a.actor());
    }
    @Transactional(readOnly = true)
    public CatalogLabel.Preview preview(CatalogLabel.Kind kind, UUID source, UUID target) {
        Authorization.globalAdmin(actor()); var p = catalog.preview(kind.mergeKind(), source, target);
        var affected = p.affectedTenants().stream().map(t -> new CatalogLabel.AffectedTenant(
            (UUID) t.get("tenantId"), ((Number) t.get("referenceCount")).longValue())).toList();
        return new CatalogLabel.Preview(p.productCount(), p.assetCount(), affected, p.blockers());
    }
    @Transactional
    public void merge(CatalogLabel.Kind kind, UUID source, UUID target) {
        var a = actor(); Authorization.globalAdmin(a); merges.merge(kind.mergeKind(), source, target, a.actor());
    }
}
