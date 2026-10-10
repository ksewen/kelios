package com.github.ksewen.kelios.catalog;
import java.util.UUID;
/** Inventory-owned facts; includes retained rows regardless of state/deletion. */
public interface CatalogUsage {
    boolean assetReferences(CatalogService.Kind kind, UUID id);
    boolean productEverUsed(UUID id);
}
