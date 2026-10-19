package com.github.ksewen.kelios.inventory;

import com.github.ksewen.kelios.audit.*;
import com.github.ksewen.kelios.catalog.CatalogService;
import com.github.ksewen.kelios.catalog.CatalogService.Kind;
import com.github.ksewen.kelios.catalog.CatalogService.MergeKind;
import com.github.ksewen.kelios.identity.UserService;
import com.github.ksewen.kelios.tenancy.TenantService;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.datasource.hikari.maximum-pool-size=32"})
class DomainFoundationIT {
    @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",PG::getJdbcUrl);p.add("spring.datasource.username",PG::getUsername);p.add("spring.datasource.password",PG::getPassword);
    }
    static class TestClock extends Clock {
        final AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-10-10T12:00:00Z"));
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}public Instant instant(){return now.get();}
    }
    @TestConfiguration static class Time { @Bean @Primary TestClock testClock(){return new TestClock();} }
    @Autowired TestClock clock;
    @Autowired TenantService tenants;
    @Autowired UserService users;
    @Autowired CatalogService catalog;
    @Autowired PlaceService places;
    @Autowired AssetService assets;
    @Autowired CatalogMergeService merges;
    @Autowired AssetTagAllocator tags;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Autowired EntityManager em;
    @Autowired DataSource dataSource;
    final Actor actor=new Actor(UUID.randomUUID(),"Historical administrator");
    UUID tenant,category,manufacturer,product,location,owner;
    @BeforeEach void fixtures() {
        clock.now.set(Instant.parse("2026-10-10T12:00:00Z"));
        tenant=tenants.create("Tenant "+UUID.randomUUID(),actor);
        category=catalog.createCategory(null,"Notebook",actor);manufacturer=catalog.createManufacturer(null,"Lenvo",actor);
        product=catalog.createProduct(null,category,manufacturer,"Model",null,actor);
        location=places.create(PlaceService.Kind.LOCATION,tenant,"Raum 211",actor);owner=places.create(PlaceService.Kind.OWNER,tenant,"School",actor);
    }
    AssetService.View register() {return assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(product),location,owner,true,"  "," INV "," note "),actor);}
    long events(UUID asset){return jdbc.queryForObject("SELECT count(*) FROM asset_event WHERE asset_id=?",Long.class,asset);}
    long audits(String action,UUID target){return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action=? AND target_id=?",Long.class,action,target);}
    <T> T tx(Supplier<T> task){return new TransactionTemplate(tm).execute(s->task.get());}

    @Test void minimalIdentityAndHistoryNeedsNoLiveActor() {
        UUID u=users.create(UserService.Scope.TENANT,tenant,UserService.Role.USER,UserService.Status.PENDING,"User");
        users.create(UserService.Scope.GLOBAL,null,UserService.Role.ADMIN,UserService.Status.ACTIVE,"Global");
        assertThrows(IllegalArgumentException.class,()->users.create(UserService.Scope.GLOBAL,tenant,UserService.Role.USER,UserService.Status.ACTIVE,"Bad"));
        assertThrows(RuntimeException.class,()->jdbc.update("INSERT INTO app_user(id,scope,tenant_id,role,status,display_name,created_by,created_at,updated_by,updated_at) VALUES(?,'GLOBAL',?,'USER','ACTIVE','X',?,now(),?,now())",UUID.randomUUID(),tenant,actor.id(),actor.id()));
        assertThrows(RuntimeException.class,()->jdbc.update("INSERT INTO tenant(id,name,created_by,created_at,updated_by,updated_at) VALUES(?,'  ',?,now(),?,now())",UUID.randomUUID(),actor.id(),actor.id()));
        var columns=jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='app_user'",String.class);
        assertEquals(Set.of("id","scope","tenant_id","role","status","display_name","created_by","created_by_display_name","created_at","updated_by","updated_at"),new HashSet<>(columns));
        var a=register();jdbc.update("DELETE FROM app_user WHERE id=?",u);
        assertEquals(actor.displayName(),jdbc.queryForObject("SELECT actor_display_name FROM asset_event WHERE asset_id=?",String.class,a.id()));
        assertEquals(1,audits("TENANT_CREATED",tenant));assertEquals("read committed",jdbc.queryForObject("SHOW transaction_isolation",String.class));
    }
    @Test void registrationDerivesFactsNormalizesTextAndHasNoInitialLoan() {
        var a=register();assertEquals(category,a.categoryId());assertEquals(manufacturer,a.manufacturerId());assertEquals(product,a.productId());
        assertEquals(LifecycleState.REGISTERED,a.lifecycle());assertEquals(ConditionState.UNKNOWN,a.condition());assertTrue(a.loanable());
        assertNull(a.serialNumber());assertEquals("INV",a.externalInventoryNumber());assertEquals("note",a.note());assertEquals(0,a.version());
        assertNull(jdbc.queryForObject("SELECT loaned_to FROM asset WHERE id=?",String.class,a.id()));assertEquals(1,events(a.id()));
        assertEquals(product.toString(),jdbc.queryForObject("SELECT data #>> '{after,productId}' FROM asset_event WHERE asset_id=?",String.class,a.id()));
        assertThrows(NullPointerException.class,()->new AssetService.Registration(new AssetService.ProductSelection(product),location,null,null,null,null,null));
        assertThrows(NullPointerException.class,()->new AssetService.DirectSelection(null,manufacturer));
        assertEquals(List.of("productId"),Arrays.stream(AssetService.ProductSelection.class.getRecordComponents()).map(c->c.getName()).toList());
        assertFalse(Arrays.stream(AssetService.Registration.class.getRecordComponents()).anyMatch(c->c.getName().equals("loanedTo") || c.getName().equals("assetTag")));
        var direct=assets.register(tenant,new AssetService.Registration(new AssetService.DirectSelection(category,manufacturer),location,null,false,"same",null,null),actor);
        assertNull(direct.productId());assertFalse(direct.loanable());
    }
    @Test void sameTenantAndProductCompositeConstraintsAreRealPostgres() {
        var a=register();UUID other=tenants.create("Other",actor);UUID loc=places.create(PlaceService.Kind.LOCATION,other,"Other room",actor);UUID own=places.create(PlaceService.Kind.OWNER,other,"Other owner",actor);
        assertThrows(RuntimeException.class,()->jdbc.update("UPDATE asset SET location_id=? WHERE id=?",loc,a.id()));
        assertThrows(RuntimeException.class,()->jdbc.update("UPDATE asset SET owner_id=? WHERE id=?",own,a.id()));
        assertThrows(RuntimeException.class,()->jdbc.update("UPDATE asset SET category_id=? WHERE id=?",CatalogService.UNKNOWN_CATEGORY,a.id()));
        assertThrows(IllegalArgumentException.class,()->assets.get(other,a.id()));
        assertThrows(IllegalArgumentException.class,()->assets.changeLocation(tenant,a.id(),0,loc,actor));
        assertThrows(IllegalArgumentException.class,()->assets.changeOwner(tenant,a.id(),0,own,actor));
        assertThrows(IllegalArgumentException.class,()->places.edit(PlaceService.Kind.LOCATION,other,location,"Hidden",actor));
        assertEquals(1,events(a.id()));
    }
    @Test void freshSchemaAndV1UpgradeUseSameMigrations() {
        assertEquals(9,jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class));
        assertEquals(10,jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('tenant','app_user','category','manufacturer','product','location','owner','asset','asset_event','audit_log')",Integer.class));
        Flyway v1=Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas("upgrade_test").defaultSchema("upgrade_test").target("1").load();v1.migrate();
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='upgrade_test' AND table_name='asset'",Integer.class));
        Flyway all=Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas("upgrade_test").defaultSchema("upgrade_test").load();all.migrate();all.validate();
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM upgrade_test.category WHERE name='UNKNOWN'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM upgrade_test.manufacturer WHERE name='UNKNOWN'",Integer.class));
        assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND NOT t.tgisinternal",Integer.class));
    }
    @ParameterizedTest @EnumSource(Kind.class) void deletionGuardsRetainedAssets(Kind kind) {
        var a=register();jdbc.update("UPDATE asset SET lifecycle='DEREGISTERED',deleted_at=now(),deleted_by=? WHERE id=?",actor.id(),a.id());
        UUID id=switch(kind){case CATEGORY->category;case MANUFACTURER->manufacturer;case PRODUCT->product;};
        assertThrows(IllegalArgumentException.class,()->catalog.delete(kind,id,actor));
        if(kind==Kind.PRODUCT)assertThrows(IllegalArgumentException.class,()->catalog.editProduct(id,category,manufacturer,"Changed",null,actor));
        else assertThrows(IllegalArgumentException.class,()->catalog.editLabel(kind,id,"Changed",actor));
        assertFalse(catalog.read(kind,id).deleted());
    }
    @ParameterizedTest @EnumSource(LifecycleState.class) void everyLifecycleRetainsPlaceReferences(LifecycleState state) {
        var a=register();jdbc.update("UPDATE asset SET lifecycle=? WHERE id=?",state.name(),a.id());
        assertThrows(IllegalArgumentException.class,()->places.delete(PlaceService.Kind.LOCATION,tenant,location,actor));
        assertThrows(IllegalArgumentException.class,()->places.delete(PlaceService.Kind.OWNER,tenant,owner,actor));
        assertThrows(IllegalArgumentException.class,()->places.edit(PlaceService.Kind.OWNER,tenant,owner,"Changed",actor));
        places.edit(PlaceService.Kind.LOCATION,tenant,location,"Renamed",actor);
        assertEquals(location,assets.get(tenant,a.id()).locationId());assertEquals(1,events(a.id()));
        assertEquals("Raum 211",jdbc.queryForObject("SELECT data #>> '{after,location,name}' FROM asset_event WHERE asset_id=?",String.class,a.id()));
        UUID newLoc=places.create(PlaceService.Kind.LOCATION,tenant,"Valid",actor);
        var moved=assets.changeLocation(tenant,a.id(),0,newLoc,actor);assertEquals(state,moved.lifecycle());
        places.delete(PlaceService.Kind.LOCATION,tenant,location,actor);assertEquals(2,events(a.id()));
    }
    @Test void historyFreezesProductAfterAllRelationshipsRemoved() {
        var a=register();var b=assets.changeCatalog(tenant,a.id(),0,new AssetService.DirectSelection(category,manufacturer),actor);
        assertNull(b.productId());assertEquals(product.toString(),jdbc.queryForObject("SELECT data #>> '{before,productId}' FROM asset_event WHERE asset_id=? AND type='UPDATED'",String.class,a.id()));
        assertThrows(IllegalArgumentException.class,()->catalog.editProduct(product,category,manufacturer,"Changed",null,actor));
        catalog.delete(Kind.PRODUCT,product,actor);assertTrue(catalog.read(Kind.PRODUCT,product).deleted());
        assertThrows(IllegalArgumentException.class,()->catalog.editLabel(Kind.CATEGORY,category,"Still referenced",actor));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='product' AND column_name='ever_asset_referenced'",Integer.class));
    }
    @Test void softDeletedProductStillReferencesParentsAndUnreferencedEditsRemainIndependent() {
        catalog.delete(Kind.PRODUCT,product,actor);
        assertThrows(IllegalArgumentException.class,()->catalog.delete(Kind.CATEGORY,category,actor));
        assertThrows(IllegalArgumentException.class,()->catalog.editLabel(Kind.MANUFACTURER,manufacturer,"Lenovo",actor));
        UUID one=catalog.createManufacturer(null,"Lenvo",actor),two=catalog.createManufacturer(null,"Lenovo",actor);
        catalog.editLabel(Kind.MANUFACTURER,one,"Lenovo",actor);
        assertFalse(catalog.read(Kind.MANUFACTURER,one).deleted());assertFalse(catalog.read(Kind.MANUFACTURER,two).deleted());
        UUID unused=catalog.createProduct(null,category,manufacturer,"Before",null,actor);catalog.editProduct(unused,category,manufacturer,"After","  ",actor);
        assertEquals("After",catalog.read(Kind.PRODUCT,unused).name());
        places.edit(PlaceService.Kind.OWNER,tenant,owner,"Changed",actor);places.delete(PlaceService.Kind.OWNER,tenant,owner,actor);
    }
    @Test void catalogVisibilityAndPublicationLoadAuthoritativeDependencies() {
        UUID other=tenants.create("Other",actor);UUID tc=catalog.createCategory(tenant,"Local",actor),tmf=catalog.createManufacturer(tenant,"Local",actor);
        assertThrows(IllegalArgumentException.class,()->catalog.createProduct(null,tc,manufacturer,"Invalid global",null,actor));
        assertThrows(IllegalArgumentException.class,()->catalog.createProduct(other,tc,manufacturer,"Invalid tenant",null,actor));
        UUID local=catalog.createProduct(tenant,tc,tmf,"Local",null,actor);
        assertThrows(IllegalArgumentException.class,()->catalog.publish(Kind.PRODUCT,local,actor));
        assertThrows(IllegalArgumentException.class,()->assets.register(other,new AssetService.Registration(new AssetService.ProductSelection(local),location,null,false,null,null,null),actor));
        catalog.publish(Kind.CATEGORY,tc,actor);catalog.publish(Kind.MANUFACTURER,tmf,actor);catalog.publish(Kind.PRODUCT,local,actor);
        assertTrue(catalog.read(Kind.PRODUCT,local).global());assertEquals(local,catalog.read(Kind.PRODUCT,local).id());
        var a=assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(local),location,null,false,null,null,null),actor);
        assertEquals(tc,a.categoryId());assertEquals(tmf,a.manufacturerId());
        UUID hidden=catalog.createCategory(other,"Hidden",actor);
        assertThrows(IllegalArgumentException.class,()->assets.register(tenant,new AssetService.Registration(new AssetService.DirectSelection(hidden,manufacturer),location,null,false,null,null,null),actor));
        UUID deleted=catalog.createCategory(null,"Deleted",actor);catalog.delete(Kind.CATEGORY,deleted,actor);
        assertThrows(IllegalArgumentException.class,()->assets.register(tenant,new AssetService.Registration(new AssetService.DirectSelection(deleted,manufacturer),location,null,false,null,null,null),actor));
    }
    @ParameterizedTest @EnumSource(MergeKind.class) void mergeIncludesRetainedRowsAndPreservesIdentityAndHistory(MergeKind kind) {
        var a=register();jdbc.update("UPDATE asset SET lifecycle='DEREGISTERED',deleted_at=now(),deleted_by=? WHERE id=?",actor.id(),a.id());
        UUID source=kind==MergeKind.CATEGORY?category:manufacturer;
        UUID target=kind==MergeKind.CATEGORY?catalog.createCategory(null,"Target",actor):catalog.createManufacturer(null,"Target",actor);
        UUID retained=catalog.createProduct(null,category,manufacturer,"Retained",null,actor);catalog.delete(Kind.PRODUCT,retained,actor);
        merges.merge(kind,source,target,actor);
        var updated=assets.get(tenant,a.id());assertEquals(1,updated.version());assertEquals(a.assetTag(),updated.assetTag());assertEquals(a.id(),updated.id());assertEquals(a.tenantId(),updated.tenantId());assertEquals(LifecycleState.DEREGISTERED,updated.lifecycle());
        assertEquals(target,kind==MergeKind.CATEGORY?updated.categoryId():updated.manufacturerId());assertEquals(2,events(a.id()));
        assertTrue(catalog.read(Kind.valueOf(kind.name()),source).deleted());assertFalse(catalog.read(Kind.valueOf(kind.name()),target).deleted());
        assertEquals(target,kind==MergeKind.CATEGORY?catalog.read(Kind.PRODUCT,retained).categoryId():catalog.read(Kind.PRODUCT,retained).manufacturerId());
        String field=kind==MergeKind.CATEGORY?"categoryId":"manufacturerId";
        assertEquals(source.toString(),jdbc.queryForObject("SELECT data #>> '{before,"+field+"}' FROM asset_event WHERE asset_id=? AND type='UPDATED'",String.class,a.id()));
        assertEquals(target.toString(),jdbc.queryForObject("SELECT data #>> '{after,"+field+"}' FROM asset_event WHERE asset_id=? AND type='UPDATED'",String.class,a.id()));
        assertEquals(1,audits(kind+"_MERGED",source));
        assertThrows(ObjectOptimisticLockingFailureException.class,()->assets.editText(tenant,a.id(),0,null,null,"Stale",actor));
    }
    @Test void invalidMergeRollsBackEarlierProductRedirects() {
        UUID globalSource=catalog.createCategory(null,"Global",actor);
        UUID localTarget=catalog.createCategory(tenant,"Local",actor);
        UUID globallyVisible=catalog.createProduct(null,globalSource,manufacturer,"Global product",null,actor);
        assertThrows(IllegalArgumentException.class,()->merges.merge(MergeKind.CATEGORY,globalSource,localTarget,actor));
        assertEquals(globalSource,catalog.read(Kind.PRODUCT,globallyVisible).categoryId());assertFalse(catalog.read(Kind.CATEGORY,globalSource).deleted());assertEquals(0,audits("CATEGORY_MERGED",globalSource));
        assertThrows(IllegalArgumentException.class,()->merges.merge(MergeKind.CATEGORY,globalSource,globalSource,actor));
    }
    @Test void injectedHistoryAndAuditFailuresRollbackWholeCommands() {
        var a=register();UUID target=catalog.createCategory(null,"Target",actor);
        // Test-only failure injection; not a production migration or trigger strategy.
        jdbc.execute("CREATE FUNCTION test_fail_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected history failure'; END $$");
        jdbc.execute("CREATE TRIGGER test_fail_history BEFORE INSERT ON asset_event FOR EACH ROW EXECUTE FUNCTION test_fail_history()");
        try {
            assertThrows(RuntimeException.class,()->merges.merge(MergeKind.CATEGORY,category,target,actor));
            assertThrows(RuntimeException.class,()->assets.changeLocation(tenant,a.id(),0,places.create(PlaceService.Kind.LOCATION,tenant,"New",actor),actor));
        } finally {jdbc.execute("DROP TRIGGER test_fail_history ON asset_event");jdbc.execute("DROP FUNCTION test_fail_history()");}
        assertEquals(category,catalog.read(Kind.PRODUCT,product).categoryId());assertEquals(category,assets.get(tenant,a.id()).categoryId());assertEquals(0,assets.get(tenant,a.id()).version());assertEquals(1,events(a.id()));assertFalse(catalog.read(Kind.CATEGORY,category).deleted());
        jdbc.execute("CREATE FUNCTION test_fail_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected audit failure'; END $$");
        jdbc.execute("CREATE TRIGGER test_fail_audit BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION test_fail_audit()");
        try {assertThrows(RuntimeException.class,()->merges.merge(MergeKind.CATEGORY,category,target,actor));}
        finally {jdbc.execute("DROP TRIGGER test_fail_audit ON audit_log");jdbc.execute("DROP FUNCTION test_fail_audit()");}
        assertEquals(0,assets.get(tenant,a.id()).version());assertEquals(1,events(a.id()));assertFalse(catalog.read(Kind.CATEGORY,category).deleted());assertEquals(0,audits("CATEGORY_MERGED",category));
    }
    @Test void protectedUnknownAndHistoryRejectDirectDml() {
        var a=register();
        for(String sql:List.of("UPDATE asset_event SET note='changed' WHERE asset_id=?","DELETE FROM asset_event WHERE asset_id=?"))
            assertThrows(RuntimeException.class,()->jdbc.update(sql,a.id()));
        assertThrows(RuntimeException.class,()->jdbc.execute("TRUNCATE asset_event"));
        for(var pair:List.of(Map.entry("category",CatalogService.UNKNOWN_CATEGORY),Map.entry("manufacturer",CatalogService.UNKNOWN_MANUFACTURER))) {
            assertThrows(RuntimeException.class,()->jdbc.update("UPDATE "+pair.getKey()+" SET name='Renamed' WHERE id=?",pair.getValue()));
            assertThrows(RuntimeException.class,()->jdbc.update("UPDATE "+pair.getKey()+" SET deleted_at=now(),deleted_by=? WHERE id=?",actor.id(),pair.getValue()));
            assertThrows(RuntimeException.class,()->jdbc.update("DELETE FROM "+pair.getKey()+" WHERE id=?",pair.getValue()));
        }
        assertThrows(IllegalArgumentException.class,()->merges.merge(MergeKind.CATEGORY,CatalogService.UNKNOWN_CATEGORY,category,actor));
        assertThrows(IllegalArgumentException.class,()->catalog.editLabel(Kind.MANUFACTURER,CatalogService.UNKNOWN_MANUFACTURER,"Renamed",actor));
        assertEquals(1,events(a.id()));
    }
    @Test void assetOptimisticLockingAndIdentitySurviveSupportedEdits() {
        var a=register();var b=assets.editText(tenant,a.id(),0,"SN","External","New",actor);
        assertEquals(1,b.version());assertEquals(a.id(),b.id());assertEquals(a.tenantId(),b.tenantId());assertEquals(a.assetTag(),b.assetTag());
        assertThrows(ObjectOptimisticLockingFailureException.class,()->assets.delete(tenant,a.id(),0,actor));
        var removed=assets.changeOwner(tenant,a.id(),1,null,actor);places.delete(PlaceService.Kind.OWNER,tenant,owner,actor);
        assets.delete(tenant,a.id(),removed.version(),actor);assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM asset WHERE id=?",Integer.class,a.id()));assertEquals(4,events(a.id()));
    }
    @Test void tagUtcBoundaryExpansionAndUniqueness() {
        clock.now.set(Instant.parse("2030-01-01T23:59:59Z"));var before=register();assertEquals("20300101-001",before.assetTag());
        clock.now.set(Instant.parse("2030-01-02T00:00:00Z"));var after=register();assertEquals("20300102-001",after.assetTag());
        jdbc.update("UPDATE asset_tag_counter SET last_value=999 WHERE registration_date='2030-01-02'");assertEquals("20300102-1000",register().assetTag());
        assertThrows(RuntimeException.class,()->jdbc.update("UPDATE asset SET asset_tag=? WHERE id=?",before.assetTag(),after.id()));
        assertThrows(RuntimeException.class,()->jdbc.update("UPDATE asset SET loan_note='Orphan' WHERE id=?",before.id()));
    }
    @Test void concurrentTagAllocationAcrossTenants() throws Exception {
        clock.now.set(Instant.parse("2031-05-01T00:00:00Z"));UUID second=tenants.create("Second",actor);UUID loc=places.create(PlaceService.Kind.LOCATION,second,"Room",actor);
        try(var pool=Executors.newFixedThreadPool(8)) {
            List<Future<String>> futures=new ArrayList<>();
            for(int i=0;i<24;i++){boolean other=i%2==0;futures.add(pool.submit(()->assets.register(other?second:tenant,new AssetService.Registration(new AssetService.ProductSelection(product),other?loc:location,null,false,null,null,null),actor).assetTag()));}
            Set<String> tags=new HashSet<>();for(var f:futures)tags.add(f.get(15,TimeUnit.SECONDS));assertEquals(24,tags.size());assertTrue(tags.contains("20310501-001"));assertTrue(tags.contains("20310501-024"));
        }
    }
    @Test void noUnsupportedMergeOrBusinessEndpointsExist() throws Exception {
        assertEquals(Set.of("CATEGORY","MANUFACTURER"),new HashSet<>(Arrays.stream(MergeKind.values()).map(Enum::name).toList()));
        assertFalse(Arrays.stream(PlaceService.class.getDeclaredMethods()).anyMatch(m->m.getName().toLowerCase(Locale.ROOT).contains("merge")));
        var root=java.nio.file.Path.of("src/main/java");try(var files=java.nio.file.Files.walk(root)) {
            for(var path:files.filter(p->p.toString().endsWith(".java")).toList()) {
                String source=java.nio.file.Files.readString(path);
                if(source.contains("@RestController"))assertTrue(path.toString().contains("platform/StatusController") || path.toString().contains("platform/security/AuthController") || path.toString().contains("platform/security/IdentityAdminController") || path.toString().contains("platform/security/AuthProblems") || java.util.Set.of("AssetController.java","CatalogController.java","PlaceController.java","AuditController.java","BusinessProblems.java").contains(path.getFileName().toString()));
                assertFalse(source.contains("SERIALIZABLE"));assertFalse(source.contains("ever_asset_referenced"));
            }
        }
    }
    private void named(String name) {jdbc.queryForObject("SELECT set_config('application_name',?,true)",String.class,name);}
    private void await(CountDownLatch latch) {
        try {assertTrue(latch.await(10,TimeUnit.SECONDS),"Timed out waiting for transaction gate");}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new AssertionError(ex);}
    }
    private void blocked(String name) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline) {
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')",Boolean.class,name)))return;
            Thread.sleep(20);
        }
        fail("Transaction did not block on an actual PostgreSQL lock: "+name);
    }
    private void fails(Future<?> future,Class<? extends Throwable> expected) throws Exception {
        ExecutionException ex=assertThrows(ExecutionException.class,()->future.get(10,TimeUnit.SECONDS));
        Throwable cause=ex.getCause();assertTrue(expected.isInstance(cause),()->"Expected "+expected+", got "+cause);
    }
    @ParameterizedTest @ValueSource(strings={"category","manufacturer","product","location","owner"})
    void incomingReferenceWinsAndGuardWaitsForFreshInspection(String type) throws Exception {
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);String name="guard_"+UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> writer=pool.submit(()->tx(()->{register();held.countDown();await(release);return null;}));await(held);
            Future<?> guard=pool.submit(()->tx(()->{named(name);
                switch(type) {
                    case "category"->catalog.editLabel(Kind.CATEGORY,category,"Changed",actor);
                    case "manufacturer"->catalog.delete(Kind.MANUFACTURER,manufacturer,actor);
                    case "product"->catalog.editProduct(product,category,manufacturer,"Changed",null,actor);
                    case "location"->places.delete(PlaceService.Kind.LOCATION,tenant,location,actor);
                    case "owner"->places.edit(PlaceService.Kind.OWNER,tenant,owner,"Changed",actor);
                    default->throw new AssertionError(type);
                }return null;}));
            try {blocked(name);assertFalse(guard.isDone());}finally{release.countDown();}
            writer.get(10,TimeUnit.SECONDS);fails(guard,IllegalArgumentException.class);
        } finally {release.countDown();}
    }
    @ParameterizedTest @EnumSource(value=Kind.class,names={"CATEGORY","MANUFACTURER"})
    void retainedProductAssignmentProtectsEmptyCatalogGuard(Kind kind) throws Exception {
        UUID empty=kind==Kind.CATEGORY?catalog.createCategory(null,"Empty",actor):catalog.createManufacturer(null,"Empty",actor);
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);String name="catalog_guard_"+UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> writer=pool.submit(()->tx(()->{catalog.createProduct(null,kind==Kind.CATEGORY?empty:category,kind==Kind.MANUFACTURER?empty:manufacturer,"New",null,actor);held.countDown();await(release);return null;}));await(held);
            Future<?> guard=pool.submit(()->tx(()->{named(name);catalog.delete(kind,empty,actor);return null;}));
            try{blocked(name);}finally{release.countDown();}writer.get(10,TimeUnit.SECONDS);fails(guard,IllegalArgumentException.class);
        }finally{release.countDown();}
        assertFalse(catalog.read(kind,empty).deleted());
    }
    @Test void deletionWinsEmptyGuardAndWaitingAssignmentRejectsDeletedTarget() throws Exception {
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);String name="assign_"+UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> deletion=pool.submit(()->tx(()->{places.delete(PlaceService.Kind.LOCATION,tenant,location,actor);held.countDown();await(release);return null;}));await(held);
            Future<?> assignment=pool.submit(()->tx(()->{named(name);return register();}));
            try{blocked(name);}finally{release.countDown();}deletion.get(10,TimeUnit.SECONDS);fails(assignment,IllegalArgumentException.class);
        }finally{release.countDown();}
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM asset WHERE tenant_id=?",Integer.class,tenant));
    }
    @Test void productEditWinsFirstUseAndRegistrationLoadsEditedFacts() throws Exception {
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);String name="first_use_"+UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> edit=pool.submit(()->tx(()->{catalog.editProduct(product,category,manufacturer,"Edited",null,actor);held.countDown();await(release);return null;}));await(held);
            Future<AssetService.View> registration=pool.submit(()->tx(()->{named(name);return register();}));
            try{blocked(name);}finally{release.countDown();}edit.get(10,TimeUnit.SECONDS);var a=registration.get(10,TimeUnit.SECONDS);
            assertEquals("Edited",jdbc.queryForObject("SELECT data #>> '{after,product,name}' FROM asset_event WHERE asset_id=?",String.class,a.id()));
        }finally{release.countDown();}
    }
    @Test void mergeWinsAndNewAssignmentCannotAttachToDeletedSource() throws Exception {
        var old=register();UUID target=catalog.createCategory(null,"Target",actor);
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);String name="merge_assign_"+UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> merge=pool.submit(()->tx(()->{merges.merge(MergeKind.CATEGORY,category,target,actor);held.countDown();await(release);return null;}));await(held);
            Future<?> assignment=pool.submit(()->tx(()->{named(name);return assets.register(tenant,new AssetService.Registration(new AssetService.DirectSelection(category,manufacturer),location,null,false,null,null,null),actor);}));
            try{blocked(name);}finally{release.countDown();}merge.get(10,TimeUnit.SECONDS);fails(assignment,IllegalArgumentException.class);
        }finally{release.countDown();}
        assertEquals(target,assets.get(tenant,old.id()).categoryId());assertEquals(2,events(old.id()));
    }
    @Test void incomingAssignmentWinsAndMergeDiscoversItAfterWaiting() throws Exception {
        UUID target=catalog.createManufacturer(null,"Target",actor);CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);String name="waiting_merge_"+UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<AssetService.View> registration=pool.submit(()->tx(()->{var a=register();held.countDown();await(release);return a;}));await(held);
            Future<?> merge=pool.submit(()->tx(()->{named(name);merges.merge(MergeKind.MANUFACTURER,manufacturer,target,actor);return null;}));
            try{blocked(name);}finally{release.countDown();}var a=registration.get(10,TimeUnit.SECONDS);merge.get(10,TimeUnit.SECONDS);
            assertEquals(target,assets.get(tenant,a.id()).manufacturerId());assertEquals(2,events(a.id()));
        }finally{release.countDown();}
    }
    @Test void simultaneousAssetUpdatesUseVersionPredicateAndRollbackLosingEvent() throws Exception {
        var a=register();CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);String name="optimistic_"+UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> first=pool.submit(()->tx(()->{assets.editText(tenant,a.id(),0,null,null,"Winner",actor);held.countDown();await(release);return null;}));await(held);
            Future<?> second=pool.submit(()->tx(()->{named(name);return assets.editText(tenant,a.id(),0,null,null,"Loser",actor);}));
            try{blocked(name);}finally{release.countDown();}first.get(10,TimeUnit.SECONDS);fails(second,ObjectOptimisticLockingFailureException.class);
        }finally{release.countDown();}
        assertEquals("Winner",assets.get(tenant,a.id()).note());assertEquals(2,events(a.id()));assertEquals(1,assets.get(tenant,a.id()).version());
    }
    @Test void operationLockTimeoutRollsBackMergeWithoutRetryOrPartialHistory() throws Exception {
        var a=register();UUID target=catalog.createCategory(null,"Target",actor);CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> reference=pool.submit(()->tx(()->{catalog.share(Kind.CATEGORY,category);held.countDown();await(release);return null;}));await(held);
            Future<?> merge=pool.submit(()->tx(()->{jdbc.execute("SET LOCAL lock_timeout='200ms'");merges.merge(MergeKind.CATEGORY,category,target,actor);return null;}));
            try{fails(merge,org.springframework.dao.PessimisticLockingFailureException.class);}finally{release.countDown();}reference.get(10,TimeUnit.SECONDS);
        }finally{release.countDown();}
        assertEquals(category,assets.get(tenant,a.id()).categoryId());assertEquals(category,catalog.read(Kind.PRODUCT,product).categoryId());assertEquals(1,events(a.id()));assertFalse(catalog.read(Kind.CATEGORY,category).deleted());assertEquals(0,audits("CATEGORY_MERGED",category));
    }
    @Test void unrelatedLocationMetadataIsNotPartOfGlobalLockProtocol() throws Exception {
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> guarded=pool.submit(()->tx(()->{jdbc.queryForObject("SELECT id FROM category WHERE id=? FOR UPDATE",UUID.class,category);held.countDown();await(release);return null;}));await(held);
            Future<?> unrelated=pool.submit(()->places.edit(PlaceService.Kind.LOCATION,tenant,location,"Independent",actor));
            try{unrelated.get(5,TimeUnit.SECONDS);}finally{release.countDown();}guarded.get(10,TimeUnit.SECONDS);
        }finally{release.countDown();}
    }
    static java.util.stream.Stream<Arguments> visibilityCases() {
        return java.util.stream.Stream.of("GLOBAL","SAME","OTHER").flatMap(scope ->
            java.util.stream.Stream.of("GLOBAL","SAME","OTHER").flatMap(cat ->
                java.util.stream.Stream.of("GLOBAL","SAME","OTHER").map(man -> Arguments.of(scope,cat,man))));
    }
    @ParameterizedTest @MethodSource("visibilityCases")
    void completeProductDependencyVisibilityMatrix(String productScope,String categoryScope,String manufacturerScope) {
        UUID other=tenants.create("Other",actor);
        UUID productTenant=switch(productScope){case "GLOBAL"->null;case "SAME"->tenant;default->other;};
        UUID catTenant=switch(categoryScope){case "GLOBAL"->null;case "SAME"->tenant;default->other;};
        UUID manTenant=switch(manufacturerScope){case "GLOBAL"->null;case "SAME"->tenant;default->other;};
        UUID cat=catalog.createCategory(catTenant,"Category",actor),man=catalog.createManufacturer(manTenant,"Manufacturer",actor);
        boolean allowed=(catTenant==null || Objects.equals(catTenant,productTenant)) && (manTenant==null || Objects.equals(manTenant,productTenant));
        Supplier<UUID> create=()->catalog.createProduct(productTenant,cat,man,"Product",null,actor);
        if(allowed)assertNotNull(create.get());else assertThrows(IllegalArgumentException.class,create::get);
    }
    @Test void invalidAssetVisibilityRollsBackAlreadyRedirectedProducts() {
        UUID other=tenants.create("Other",actor),otherLoc=places.create(PlaceService.Kind.LOCATION,other,"Room",actor);
        UUID source=catalog.createCategory(null,"Source",actor),target=catalog.createCategory(tenant,"Target",actor);
        UUID localProduct=catalog.createProduct(tenant,source,manufacturer,"Local",null,actor);
        var foreignAsset=assets.register(other,new AssetService.Registration(new AssetService.DirectSelection(source,manufacturer),otherLoc,null,false,null,null,null),actor);
        assertThrows(IllegalArgumentException.class,()->merges.merge(MergeKind.CATEGORY,source,target,actor));
        assertEquals(source,catalog.read(Kind.PRODUCT,localProduct).categoryId());assertEquals(source,assets.get(other,foreignAsset.id()).categoryId());
        assertEquals(0,assets.get(other,foreignAsset.id()).version());assertEquals(1,events(foreignAsset.id()));assertFalse(catalog.read(Kind.CATEGORY,source).deleted());assertEquals(0,audits("CATEGORY_MERGED",source));
    }
    @Test void unknownCanBeMergeTargetAndRetainedSoftDeletedAssetCanResolvePlaces() {
        var a=register();assets.delete(tenant,a.id(),0,actor);
        assertThrows(IllegalArgumentException.class,()->places.delete(PlaceService.Kind.LOCATION,tenant,location,actor));
        UUID next=places.create(PlaceService.Kind.LOCATION,tenant,"Next",actor);
        var moved=assets.changeLocation(tenant,a.id(),1,next,actor);assertNotNull(moved.deletedAt());places.delete(PlaceService.Kind.LOCATION,tenant,location,actor);
        merges.merge(MergeKind.CATEGORY,category,CatalogService.UNKNOWN_CATEGORY,actor);
        assertEquals(CatalogService.UNKNOWN_CATEGORY,assets.get(tenant,a.id()).categoryId());
        assertEquals("UNKNOWN",catalog.read(Kind.CATEGORY,CatalogService.UNKNOWN_CATEGORY).name());
    }
    @Test void dailyCounterOverflowAndFailedRegistrationLeaveNoPartialAssetOrEvent() {
        clock.now.set(Instant.parse("2032-01-01T00:00:00Z"));
        jdbc.update("INSERT INTO asset_tag_counter(registration_date,last_value) VALUES('2032-01-01',?)",Long.MAX_VALUE);
        assertThrows(RuntimeException.class,this::register);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM asset WHERE tenant_id=?",Integer.class,tenant));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM asset_event WHERE tenant_id=?",Integer.class,tenant));
    }
    @Test void overlappingRowLockDeadlockAbortsOneWholeTransactionWithoutRetry() throws Exception {
        UUID left=places.create(PlaceService.Kind.LOCATION,tenant,"Left",actor),right=places.create(PlaceService.Kind.LOCATION,tenant,"Right",actor);
        CyclicBarrier barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            java.util.function.BiFunction<UUID,UUID,Future<?>> worker=(first,second)->pool.submit(()->tx(()->{
                jdbc.execute("SET LOCAL deadlock_timeout='100ms'");
                places.edit(PlaceService.Kind.LOCATION,tenant,first,"First",actor);
                try{barrier.await(5,TimeUnit.SECONDS);}catch(Exception ex){throw new AssertionError(ex);}
                places.edit(PlaceService.Kind.LOCATION,tenant,second,"Second",actor);return null;
            }));
            var a=worker.apply(left,right);var b=worker.apply(right,left);int success=0,conflict=0;
            for(var f:List.of(a,b)) {
                try{f.get(10,TimeUnit.SECONDS);success++;}
                catch(ExecutionException ex){assertInstanceOf(org.springframework.dao.PessimisticLockingFailureException.class,ex.getCause());conflict++;}
            }
            assertEquals(1,success);assertEquals(1,conflict);
            assertEquals(1,audits("LOCATION_EDITED",left));assertEquals(1,audits("LOCATION_EDITED",right));
        }
    }
    @Test void registrationRejectsStaleProductDependenciesAfterWaitingForMerge() throws Exception {
        UUID target=catalog.createCategory(null,"Target",actor);
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);String name="product_merge_"+UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> merge=pool.submit(()->tx(()->{merges.merge(MergeKind.CATEGORY,category,target,actor);held.countDown();await(release);return null;}));await(held);
            // The initial unlocked Product read sees pre-commit IDs; its source Category lock waits.
            Future<?> registration=pool.submit(()->tx(()->{named(name);return register();}));
            try{blocked(name);}finally{release.countDown();}merge.get(10,TimeUnit.SECONDS);fails(registration,ObjectOptimisticLockingFailureException.class);
        }finally{release.countDown();}
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM asset WHERE tenant_id=?",Integer.class,tenant));
        var fresh=register();assertEquals(target,fresh.categoryId());
    }
    @Test void mergeVersionProtectsAlreadyLoadedAssetEdit() throws Exception {
        var a=register();UUID target=catalog.createCategory(null,"Target",actor);
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);String name="merge_version_"+UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> merge=pool.submit(()->tx(()->{merges.merge(MergeKind.CATEGORY,category,target,actor);held.countDown();await(release);return null;}));await(held);
            Future<?> edit=pool.submit(()->tx(()->{named(name);return assets.editText(tenant,a.id(),0,null,null,"Stale",actor);}));
            try{blocked(name);}finally{release.countDown();}merge.get(10,TimeUnit.SECONDS);fails(edit,ObjectOptimisticLockingFailureException.class);
        }finally{release.countDown();}
        assertEquals(target,assets.get(tenant,a.id()).categoryId());assertEquals(1,assets.get(tenant,a.id()).version());assertEquals(2,events(a.id()));assertEquals("note",assets.get(tenant,a.id()).note());
    }
    @Test void administrativeAuditCapturesProductMutableInputBeforeAndAfter() {
        catalog.editProduct(product,category,manufacturer,"Renamed","Part-123",actor);
        assertEquals("Model",jdbc.queryForObject("SELECT data #>> '{before,name}' FROM audit_log WHERE target_id=? AND action='PRODUCT_EDITED'",String.class,product));
        assertEquals("Part-123",jdbc.queryForObject("SELECT data #>> '{after,manufacturerNumber}' FROM audit_log WHERE target_id=? AND action='PRODUCT_EDITED'",String.class,product));
        assertEquals(actor.displayName(),jdbc.queryForObject("SELECT actor_display_name FROM audit_log WHERE target_id=? AND action='PRODUCT_EDITED'",String.class,product));
    }
    @Test void explicitUnchangedTextCommandStillAdvancesVersionAtFixedServerTime() {
        var a=register();var updated=assets.editText(tenant,a.id(),a.version(),a.serialNumber(),a.externalInventoryNumber(),a.note(),actor);
        assertEquals(a.version()+1,updated.version());assertEquals(2,events(a.id()));
    }

}
