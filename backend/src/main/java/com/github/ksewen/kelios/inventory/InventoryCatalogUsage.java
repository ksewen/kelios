package com.github.ksewen.kelios.inventory;
import com.github.ksewen.kelios.catalog.CatalogUsage;
import com.github.ksewen.kelios.catalog.CatalogService.Kind;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
class InventoryCatalogUsage implements CatalogUsage {
    private final JdbcTemplate jdbc;
    InventoryCatalogUsage(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean assetReferences(Kind kind, UUID id) {
        String column=switch(kind) {case CATEGORY->"category_id";case MANUFACTURER->"manufacturer_id";case PRODUCT->"product_id";};
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM asset WHERE "+column+"=?)",Boolean.class,id));
    }
    public boolean productEverUsed(UUID id) {
        return assetReferences(Kind.PRODUCT,id) || Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM asset_event WHERE data #>> '{before,productId}' = ? OR data #>> '{after,productId}' = ?)",Boolean.class,id.toString(),id.toString()));
    }
}
