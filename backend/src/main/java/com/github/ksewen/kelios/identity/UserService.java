package com.github.ksewen.kelios.identity;
import com.github.ksewen.kelios.tenancy.TenantService;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class UserService {
    public enum Scope { GLOBAL, TENANT }
    public enum Role { ADMIN, USER }
    public enum Status { PENDING, ACTIVE, DISABLED }
    private final EntityManager em;
    private final TenantService tenants;
    private final java.time.Clock clock;
    public UserService(EntityManager em, TenantService tenants, java.time.Clock clock) { this.em=em; this.tenants=tenants; this.clock=clock; }
    /** Trusted internal infrastructure creation. HTTP bootstrap/JIT/admin creation use their real Actor instead. */
    @Transactional public UUID create(Scope scope, UUID tenantId, Role role, Status status, String displayName) {
        if(scope==null || role==null || status==null || displayName==null || displayName.isBlank()
            || (scope==Scope.GLOBAL) != (tenantId==null)) throw new IllegalArgumentException("Invalid User foundation");
        if(tenantId!=null) tenants.require(tenantId);
        User u=new User();u.id=UUID.randomUUID();u.scope=scope.name();u.tenantId=tenantId;u.role=role.name();u.status=status.name();u.displayName=displayName.strip();u.createdBy=com.github.ksewen.kelios.audit.SystemActor.ID;u.updatedBy=u.createdBy;u.createdAt=java.time.Instant.now(clock);u.updatedAt=u.createdAt;em.persist(u);return u.id;
    }
}
