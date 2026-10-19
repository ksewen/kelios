package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.audit.Actor;
import com.github.ksewen.kelios.catalog.*;
import com.github.ksewen.kelios.identity.UserService.*;
import com.github.ksewen.kelios.tenancy.TenantService;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic regression for the label-before-Product mutation order. */
@Testcontainers
@SpringBootTest(properties={"spring.datasource.hikari.maximum-pool-size=8"})
class CatalogProductLockOrderIT {
 @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17-alpine");
 static final AuthTestKeys KEYS;
 static {try {KEYS=new AuthTestKeys("catalog-lock-order");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("spring.datasource.url",PG::getJdbcUrl);p.add("spring.datasource.username",PG::getUsername);p.add("spring.datasource.password",PG::getPassword);p.add("kelios.auth.key-directory",()->KEYS.directory.toAbsolutePath().toString());p.add("kelios.auth.public-origin",()->"https://kelios.example");p.add("kelios.auth.issuer",()->"https://kelios.example");}
 @Autowired CatalogService catalog; @Autowired ProductOperations products; @Autowired CatalogLabelOperations labels;
 @Autowired TenantService tenants; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager tm;

 @ParameterizedTest @CsvSource({"CATEGORY,edit,true","MANUFACTURER,edit,true","CATEGORY,publish,true","MANUFACTURER,publish,true","CATEGORY,combined,true","MANUFACTURER,combined,true","CATEGORY,edit,false","MANUFACTURER,edit,false","CATEGORY,publish,false","MANUFACTURER,publish,false","CATEGORY,combined,false","MANUFACTURER,combined,false"})
 void supportedCommandsSerializeOrConflictWithoutDeadlock(String resource,String command,boolean mergeFirst)throws Exception {
  var kind=CatalogLabel.Kind.valueOf(resource);var legacy=CatalogService.MergeKind.valueOf(resource);
  var actor=new Actor(UUID.randomUUID(),"Lock investigation administrator");
  var identity=new Identity(actor.id(),Scope.GLOBAL,null,Role.ADMIN,Status.ACTIVE,actor.displayName());
  UUID tenant=tenants.create("Isolated lock investigation",actor);
  UUID category=catalog.createCategory(null,"Category",actor),manufacturer=catalog.createManufacturer(null,"Manufacturer",actor);
  UUID source=kind==CatalogLabel.Kind.CATEGORY?category:manufacturer;
  UUID target=kind==CatalogLabel.Kind.CATEGORY?catalog.createCategory(null,"Target",actor):catalog.createManufacturer(null,"Target",actor);
  UUID product=catalog.createProduct(command.equals("edit")?null:tenant,category,manufacturer,"Product",null,actor);
  var creation=jdbc.queryForMap("SELECT created_by,created_at FROM product WHERE id=?",product);
  var held=new CountDownLatch(1);var proceed=new CountDownLatch(1);var waitingPid=new CompletableFuture<Integer>();
  Runnable productCommand=()->{if(command.equals("edit"))products.edit(product,new ProductModel.Command("Edited",category,manufacturer,null));else if(command.equals("combined"))products.edit(product,new ProductModel.Command("Edited",category,manufacturer,null),true);else products.publish(product);};
  Runnable mergeCommand=()->labels.merge(kind,source,target);
  try(var pool=Executors.newFixedThreadPool(2)) {
   var first=pool.submit(()->run(identity,()->{
    if(mergeFirst)catalog.lockMerge(legacy,source,target);
    else {catalog.share(CatalogService.Kind.CATEGORY,category);catalog.share(CatalogService.Kind.MANUFACTURER,manufacturer);}
    held.countDown();await(proceed);
    if(mergeFirst)mergeCommand.run();else productCommand.run();
   }));
   assertTrue(held.await(10,TimeUnit.SECONDS));
   var second=pool.submit(()->run(identity,()->{
    waitingPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
    if(mergeFirst)productCommand.run();else mergeCommand.run();
   }));
   int pid=waitingPid.get(10,TimeUnit.SECONDS);
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean blocked=false;
   while(System.nanoTime()<deadline){blocked=Boolean.TRUE.equals(jdbc.queryForObject("SELECT cardinality(pg_blocking_pids(?))>0",Boolean.class,pid));if(blocked)break;Thread.sleep(10);}
   assertTrue(blocked,"Second operation must actually wait on a label lock");proceed.countDown();
   Throwable a=first.get(20,TimeUnit.SECONDS),b=second.get(20,TimeUnit.SECONDS);
   assertNull(a,"First operation commits");assertFalse(deadlock(b));
   if(mergeFirst)assertInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class,b);
   else assertNull(b,"Both serialize and commit when Product runs first");
   System.out.println("LOCK_ORDER_EVIDENCE "+resource+" "+command+"; mergeFirst="+mergeFirst+"; outcome="+(b==null?"both committed":"reference conflict, Merge committed"));
   long edits=jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action='PRODUCT_EDITED'",Long.class,product);
   long published=jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action='PRODUCT_PUBLISHED'",Long.class,product);
   long merged=jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action=?",Long.class,source,resource+"_MERGED");
   assertEquals(mergeFirst?0:command.equals("publish")?0:1,edits);assertEquals(mergeFirst?0:command.equals("edit")?0:1,published);assertEquals(1,merged);
   assertEquals(creation,jdbc.queryForMap("SELECT created_by,created_at FROM product WHERE id=?",product));
   assertEquals(actor.id(),jdbc.queryForObject("SELECT updated_by FROM product WHERE id=?",UUID.class,product));
   assertEquals(target,jdbc.queryForObject("SELECT "+(resource.equals("CATEGORY")?"category_id":"manufacturer_id")+" FROM product WHERE id=?",UUID.class,product));
   assertEquals(command.equals("edit")||!mergeFirst?"GLOBAL":"TENANT",jdbc.queryForObject("SELECT visibility FROM product WHERE id=?",String.class,product));
  }finally{proceed.countDown();}
 }
 @Test void lockedProductOwnershipIsRevalidatedAfterPublication(){
  var actor=new Actor(UUID.randomUUID(),"Publisher");UUID tenant=tenants.create("Tenant",actor);
  UUID c=catalog.createCategory(null,"Category",actor),m=catalog.createManufacturer(null,"Manufacturer",actor);
  UUID p=catalog.createProduct(tenant,c,m,"Product",null,actor);catalog.publish(CatalogService.Kind.PRODUCT,p,actor);
  var denied=assertThrows(com.github.ksewen.kelios.platform.BusinessFailure.class,()->catalog.editProduct(p,c,m,"Unauthorized",null,actor,tenant));
  assertEquals("FORBIDDEN",denied.code());assertEquals("Product",jdbc.queryForObject("SELECT name FROM product WHERE id=?",String.class,p));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action='PRODUCT_EDITED'",Integer.class,p));
 }
 Throwable run(Identity identity,Runnable operation){
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity,null,List.of()));
  try{new TransactionTemplate(tm).executeWithoutResult(s->{jdbc.execute("SET LOCAL statement_timeout='15s'");operation.run();});return null;}
  catch(Throwable t){return t;}finally{SecurityContextHolder.clearContext();}
 }
 static void await(CountDownLatch latch){try{assertTrue(latch.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}}
 static SQLException sql(Throwable t){while(t!=null){if(t instanceof SQLException s && "40P01".equals(s.getSQLState()))return s;t=t.getCause();}return null;}
 static boolean deadlock(Throwable t){return sql(t)!=null;}
}
