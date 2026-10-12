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
    private final java.time.Clock clock;
    public TenantService(EntityManager em, AuditService audit, java.time.Clock clock) { this.em=em; this.audit=audit; this.clock=clock; }
    @Transactional public UUID create(String name, Actor actor) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Tenant name required");
        Tenant tenant=new Tenant(); tenant.id=UUID.randomUUID(); tenant.name=name.strip(); tenant.createdBy=actor.id(); tenant.updatedBy=actor.id(); tenant.createdAt=java.time.Instant.now(clock); tenant.updatedAt=tenant.createdAt; em.persist(tenant); em.flush();
        audit.record(actor,tenant.id,"TENANT_CREATED","TENANT",tenant.id,Map.of("name",tenant.name)); return tenant.id;
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lock(UUID id) {
        if(id==null || em.find(Tenant.class,id,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)==null)throw new IllegalArgumentException("Tenant not found");
    }
    @Transactional(readOnly=true) public void require(UUID id) {
        if (id==null || em.find(Tenant.class,id)==null) throw new IllegalArgumentException("Tenant not found");
    }
}
