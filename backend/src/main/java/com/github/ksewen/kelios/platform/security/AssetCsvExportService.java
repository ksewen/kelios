package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.UserService.Scope;
import com.github.ksewen.kelios.inventory.AssetQuery;
import com.github.ksewen.kelios.inventory.AssetRead;
import com.github.ksewen.kelios.platform.BusinessFailure;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only CSV adapter over the authorized Asset collection, with no independent query rules. */
@Service
public class AssetCsvExportService {
    private static final int PAGE_SIZE=100, MAX_ROWS=20_000, MAX_BYTES=20*1024*1024;
    private static final String HEADER="id,assetTag,localIdentifier,tenantId,tenant,productId,product,manufacturerId,manufacturer,categoryId,category,serialNumber,externalInventoryNumber,locationId,location,ownerId,owner,lifecycle,condition,loanable,loanedTo,loanedAt,expectedReturnAt,createdAt,createdBy,createdByDisplayName";
    private final InventoryOperations inventory;
    public AssetCsvExportService(InventoryOperations inventory){this.inventory=inventory;}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public byte[] export(Map<String,String> input){
        var parameters=new java.util.LinkedHashMap<>(input);
        String delimiter=parameters.remove("delimiter");
        char separator=delimiter(delimiter);
        UUID tenant=com.github.ksewen.kelios.platform.ListInput.uuid(parameters.remove("tenantId"));
        parameters.remove("page");
        parameters.remove("size");
        AssetQuery criteria=AssetDtos.query(parameters);
        var actor=CurrentIdentity.require();
        if(actor.scope()==Scope.TENANT)tenant=actor.tenantId();
        var output=new StringBuilder("\ufeff");
        output.append(HEADER.replace(',',separator)).append("\r\n");
        long expected=-1;
        for(int page=0;;page++){
            var query=new AssetQuery(page,PAGE_SIZE,criteria.search(),criteria.lifecycle(),criteria.condition(),
                criteria.locationId(),criteria.ownerId(),criteria.categoryId(),criteria.manufacturerId(),criteria.productId(),
                criteria.loaned(),criteria.loanable(),criteria.localIdentifier(),criteria.serialNumber(),criteria.externalInventoryNumber(),
                criteria.sort(),criteria.direction());
            var result=actor.scope()==Scope.GLOBAL?inventory.all(tenant,query):inventory.list(tenant,query);
            if(expected<0){expected=result.totalElements();if(expected>MAX_ROWS)throw BusinessFailure.invalid("CSV export exceeds 20000 Assets");}
            for(AssetRead item:result.items()){
                append(output,separator,item);
                if(output.length()>MAX_BYTES)throw BusinessFailure.invalid("CSV export exceeds 20 MiB");
            }
            if((long)(page+1)*PAGE_SIZE>=expected)break;
        }
        byte[] bytes=output.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw BusinessFailure.invalid("CSV export exceeds 20 MiB");
        return bytes;
    }

    private static char delimiter(String value){
        if(value==null)return ';';
        if(value.length()!=1 || value.charAt(0)=='"' || value.charAt(0)=='\r' || value.charAt(0)=='\n'
            || (Character.isISOControl(value.charAt(0)) && value.charAt(0)!='\t'))
            throw BusinessFailure.invalid("Invalid CSV delimiter");
        return value.charAt(0);
    }
    private static void append(StringBuilder out,char separator,AssetRead row){
        var a=row.state();
        Object[] fields={a.id(),a.assetTag(),a.localIdentifier(),a.tenantId(),name(row.tenant()),
            a.productId(),name(row.product()),a.manufacturerId(),name(row.manufacturer()),a.categoryId(),name(row.category()),
            a.serialNumber(),a.externalInventoryNumber(),a.locationId(),name(row.location()),a.ownerId(),name(row.owner()),
            a.lifecycle(),a.condition(),a.loanable(),a.loanedTo(),a.loanedAt(),a.expectedReturnAt(),a.createdAt(),a.createdBy(),
            a.createdByDisplayName()};
        for(int i=0;i<fields.length;i++){
            if(i>0)out.append(separator);
            cell(out,fields[i]);
        }
        out.append("\r\n");
    }
    private static String name(AssetRead.Reference reference){return reference==null?null:reference.name();}
    private static void cell(StringBuilder out,Object value){
        if(value==null)return;
        String text=value.toString();
        String leading=text.stripLeading();
        if(!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0))>=0)text="'"+text;
        out.append('"');
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='"')out.append('"');out.append(c);}
        out.append('"');
    }
}
