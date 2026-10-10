package com.github.ksewen.kelios.audit;
import com.github.ksewen.kelios.platform.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class AuditReads {
    private final JdbcTemplate jdbc;
    public AuditReads(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Page<Map<String,Object>> list(UUID authenticatedTenant,boolean globalAdmin,Map<String,String> q){
        ListInput.keys(q,"action","targetType","targetId","tenantId","globalOnly");if(q.containsKey("q"))throw BusinessFailure.invalid("Audit has no text search");
        int page=ListInput.page(q),size=ListInput.size(q);var args=new ArrayList<Object>();StringBuilder where=new StringBuilder(" WHERE true");
        if(!globalAdmin){if(authenticatedTenant==null)throw new BusinessFailure("FORBIDDEN","Audit access denied");where.append(" AND tenant_id=?");args.add(authenticatedTenant);if(q.containsKey("tenantId")||q.containsKey("globalOnly"))throw BusinessFailure.invalid("Audit scope filters are GLOBAL ADMIN only");}
        else {
            if(q.containsKey("tenantId") && q.containsKey("globalOnly"))throw BusinessFailure.invalid("Incompatible audit filters");
            if(q.containsKey("tenantId")){where.append(" AND tenant_id=?");args.add(ListInput.uuid(q.get("tenantId")));}
            if(q.containsKey("globalOnly") && ListInput.bool(q.get("globalOnly")))where.append(" AND tenant_id IS NULL");
        }
        for(String[] f:new String[][]{{"action","action"},{"targetType","target_type"},{"targetId","target_id"}})if(q.containsKey(f[0])){where.append(" AND ").append(f[1]).append("=?");args.add(f[0].equals("targetId")?ListInput.uuid(q.get(f[0])):q.get(f[0]));}
        long count=jdbc.queryForObject("SELECT count(*) FROM audit_log"+where,Long.class,args.toArray());args.add(size);args.add((long)page*size);
        return new Page<>(jdbc.query("SELECT * FROM audit_log"+where+" ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",JdbcRows.MAP,args.toArray()),page,size,count);
    }
    public Map<String,Object> detail(UUID id,UUID authenticatedTenant,boolean globalAdmin){
        var args=new ArrayList<Object>(List.of(id));String scope="";
        if(!globalAdmin){if(authenticatedTenant==null)throw new BusinessFailure("FORBIDDEN","Audit access denied");scope=" AND tenant_id=?";args.add(authenticatedTenant);}
        var rows=jdbc.query("SELECT * FROM audit_log WHERE id=?"+scope,JdbcRows.MAP,args.toArray());if(rows.isEmpty())throw BusinessFailure.missing();return rows.getFirst();
    }
}
