package com.github.ksewen.kelios.inventory;

import java.util.UUID;
import com.github.ksewen.kelios.platform.BusinessFailure;

/** Authorized scope is supplied separately, never embedded as client authority in criteria. */
public record AssetQuery(int page,int size,String search,LifecycleState lifecycle,ConditionState condition,
        UUID locationId,UUID ownerId,UUID categoryId,UUID manufacturerId,UUID productId,
        Boolean loaned,Boolean loanable,String localIdentifier,String serialNumber,String externalInventoryNumber,
        Sort sort,Direction direction) {
    public enum Sort { ASSET_TAG, CREATED_AT, LIFECYCLE, CONDITION }
    public enum Direction { ASC, DESC }
    public AssetQuery {
        if(page<0 || size<1 || size>100)throw BusinessFailure.invalid("Invalid pagination");
        search=text(search);localIdentifier=text(localIdentifier);serialNumber=text(serialNumber);externalInventoryNumber=text(externalInventoryNumber);
        if(sort==null)sort=Sort.CREATED_AT;
        if(direction==null)direction=Direction.DESC;
    }
    private static String text(String value){
        if(value==null)return null;value=value.strip();
        if(value.length()>128)throw BusinessFailure.invalid("Search exceeds 128 characters");
        return value.isEmpty()?null:value;
    }
}
