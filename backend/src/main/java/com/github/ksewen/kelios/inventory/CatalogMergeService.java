package com.github.ksewen.kelios.inventory;

import com.github.ksewen.kelios.audit.Actor;
import com.github.ksewen.kelios.catalog.CatalogService;
import com.github.ksewen.kelios.catalog.CatalogService.Kind;
import com.github.ksewen.kelios.catalog.CatalogService.MergeKind;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit cross-module transaction coordinator, internal until Phase 2/3 authorization and UI. */
@Service
public class CatalogMergeService {
    private final CatalogService catalog;
    private final AssetService assets;
    private final AssetHistory history;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Clock clock;
    public CatalogMergeService(CatalogService catalog,AssetService assets,AssetHistory history,JdbcTemplate jdbc,EntityManager em,Clock clock) {
        this.catalog=catalog;this.assets=assets;this.history=history;this.jdbc=jdbc;this.em=em;this.clock=clock;
    }
    @Transactional public void merge(MergeKind kind,UUID source,UUID target,Actor actor) {
        var pair=catalog.lockMerge(kind,source,target);catalog.redirectProducts(pair,actor);
        String column=kind==MergeKind.CATEGORY?"category_id":"manufacturer_id";
        var ids=jdbc.query("SELECT id FROM asset WHERE "+column+"=? ORDER BY id FOR UPDATE",(rs,n)->rs.getObject(1,UUID.class),source);
        for(UUID id:ids) {
            Asset a=em.find(Asset.class,id);em.refresh(a);
            if(!(kind==MergeKind.CATEGORY?a.categoryId:a.manufacturerId).equals(source))continue;
            catalog.requireVisible(pair.target(),a.tenantId,false);
            var before=assets.snapshot(a);
            if(kind==MergeKind.CATEGORY)a.categoryId=target;else a.manufacturerId=target;
            catalog.requireVisible(catalog.share(Kind.CATEGORY,a.categoryId),a.tenantId,false);
            catalog.requireVisible(catalog.share(Kind.MANUFACTURER,a.manufacturerId),a.tenantId,false);
            if(a.productId!=null)catalog.requireVisible(catalog.share(Kind.PRODUCT,a.productId),a.tenantId,false);
            a.updatedAt=Instant.now(clock);a.updatedBy=actor.id();em.flush();history.append(a,"UPDATED",actor,before,assets.snapshot(a));
        }
        catalog.finishMerge(pair,actor);
        em.flush(); // Deferred Product equality is checked by PostgreSQL at transaction commit.
    }
}
