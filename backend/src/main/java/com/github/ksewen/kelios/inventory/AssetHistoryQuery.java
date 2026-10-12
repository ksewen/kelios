package com.github.ksewen.kelios.inventory;

import com.github.ksewen.kelios.platform.BusinessFailure;

/** Bounded owning-Asset timeline; intentionally no general search or selectable ordering. */
public record AssetHistoryQuery(int page,int size) {
    public AssetHistoryQuery {if(page<0 || size<1 || size>100)throw BusinessFailure.invalid("Invalid pagination");}
}
