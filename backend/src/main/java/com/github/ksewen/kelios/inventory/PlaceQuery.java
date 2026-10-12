package com.github.ksewen.kelios.inventory;

import com.github.ksewen.kelios.platform.BusinessFailure;

/** Closed name-only collection criteria; note and Tenant authority are not query fields. */
public record PlaceQuery(int page,int size,String search,Sort sort,Direction direction) {
    public enum Sort { NAME }
    public enum Direction { ASC, DESC }
    public PlaceQuery {
        if(page<0 || size<1 || size>100)throw BusinessFailure.invalid("Invalid pagination");
        search=search==null?null:search.strip();
        if(search!=null && search.length()>128)throw BusinessFailure.invalid("Search exceeds 128 characters");
        if(search!=null && search.isEmpty())search=null;
        if(sort==null && direction!=null)throw BusinessFailure.invalid("Direction requires selectable sort");
        if(sort!=null && direction==null)direction=Direction.ASC;
    }
}
