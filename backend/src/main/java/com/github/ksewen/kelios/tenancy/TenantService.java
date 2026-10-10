package com.github.ksewen.kelios.tenancy;
import com.github.ksewen.kelios.audit.Actor;
import com.github.ksewen.kelios.audit.AuditService;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class TenantService {
    private final EntityManager em;
    private final AuditService audit;
    public TenantService(EntityManager em, AuditService audit) { this.em=em; this.audit=audit; }
    @Transactional public UUID create(String name, Actor actor) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Tenant name required");
        Tenant tenant=new Tenant(); tenant.id=UUID.randomUUID(); tenant.name=name.strip(); em.persist(tenant); em.flush();
        audit.record(actor,tenant.id,"TENANT_CREATED","TENANT",tenant.id,Map.of("name",tenant.name)); return tenant.id;
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lock(UUID id) {
        if(id==null || em.find(Tenant.class,id,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)==null)throw new IllegalArgumentException("Tenant not found");
    }
    public record View(UUID id,String name) {}
    @Transactional(readOnly=true) public java.util.List<View> list(UUID onlyTenant) {
        var query=em.createQuery("SELECT t FROM Tenant t"+(onlyTenant==null?"":" WHERE t.id=:id")+" ORDER BY t.id",Tenant.class);
        if(onlyTenant!=null)query.setParameter("id",onlyTenant);
        return query.getResultStream().map(t->new View(t.id,t.name)).toList();
    }
    @Transactional(readOnly=true) public void require(UUID id) {
        if (id==null || em.find(Tenant.class,id)==null) throw new IllegalArgumentException("Tenant not found");
    }
}
