package com.github.ksewen.kelios.catalog;

import java.util.UUID;
import com.github.ksewen.kelios.platform.BusinessFailure;

/** Explicit Product criteria; referenced labels and Tenant authority are not query identities. */
public record ProductQuery(int page, int size, String search, ProductModel.Visibility visibility,
        UUID categoryId, UUID manufacturerId, String manufacturerNumber, Sort sort, Direction direction) {
    public enum Sort { NAME, MANUFACTURER_NUMBER, VISIBILITY }
    public enum Direction { ASC, DESC }
    public ProductQuery {
        if (page < 0 || size < 1 || size > 100) throw BusinessFailure.invalid("Invalid pagination");
        search = text(search); manufacturerNumber = text(manufacturerNumber);
        if (sort == null && direction != null) throw BusinessFailure.invalid("Direction requires selectable sort");
        if (sort != null && direction == null) direction = Direction.ASC;
    }
    private static String text(String value) {
        if (value == null) return null;
        value = value.strip();
        if (value.length() > 128) throw BusinessFailure.invalid("Search exceeds 128 characters");
        return value.isEmpty() ? null : value;
    }
}
