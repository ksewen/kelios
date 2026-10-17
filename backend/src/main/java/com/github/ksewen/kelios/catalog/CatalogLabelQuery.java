package com.github.ksewen.kelios.catalog;

import com.github.ksewen.kelios.platform.BusinessFailure;

/** Closed resource criteria. Scope is deliberately absent: the application service derives it. */
public record CatalogLabelQuery(int page, int size, String search, CatalogLabel.Visibility visibility,
        Sort sort, Direction direction) {
    public enum Sort { NAME, VISIBILITY, TENANT_NAME }
    public enum Direction { ASC, DESC }
    public CatalogLabelQuery {
        if (page < 0 || size < 1 || size > 100) throw BusinessFailure.invalid("Invalid pagination");
        search = search == null ? null : search.strip();
        if (search != null && search.length() > 128) throw BusinessFailure.invalid("Search exceeds 128 characters");
        if (search != null && search.isEmpty()) search = null;
        if (sort == null && direction != null) throw BusinessFailure.invalid("Direction requires a selectable sort");
        if (sort != null && direction == null) direction = Direction.ASC;
    }
}
