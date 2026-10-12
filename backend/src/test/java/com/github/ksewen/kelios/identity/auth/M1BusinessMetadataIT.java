package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.audit.*;
import com.github.ksewen.kelios.catalog.CatalogService;
import com.github.ksewen.kelios.identity.UserService;
import com.github.ksewen.kelios.identity.UserService.*;
import com.github.ksewen.kelios.inventory.*;
import com.github.ksewen.kelios.platform.*;
import com.github.ksewen.kelios.platform.security.*;
import com.github.ksewen.kelios.tenancy.TenantService;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"spring.datasource.hikari.maximum-pool-size=32"})
class M1BusinessMetadataIT {
 static final class MutableClock extends Clock {
  Instant now=Instant.parse("2026-10-11T12:00:00Z");
  public Instant instant(){return now;} public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
 }
 @org.springframework.boot.test.context.TestConfiguration static class Time {
  @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary MutableClock m1Clock(){return new MutableClock();}
 }
 @Autowired MutableClock clock;
 @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17-alpine");
 static final AuthTestKeys KEYS;static{try{KEYS=new AuthTestKeys("m1-it-keys");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",PG::getJdbcUrl);p.add("spring.datasource.username",PG::getUsername);p.add("spring.datasource.password",PG::getPassword);p.add("kelios.auth.key-directory",()->KEYS.directory.toAbsolutePath().toString());p.add("kelios.auth.public-origin",()->"https://kelios.example");p.add("kelios.auth.issuer",()->"https://kelios.example");}
 @Autowired AssetService assets;@Autowired CatalogService catalog;@Autowired PlaceService places;@Autowired CatalogMergeService merges;
 @Autowired TenantService tenants;@Autowired UserService users;@Autowired IdentityStore identities;@Autowired AuthenticationService auth;
 @Autowired OidcConfigurationService configs;@Autowired OidcProvisioning provisioning;@Autowired JwtTokens tokens;
 @Autowired SecretEnvelope envelope;
 @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager tm;@Autowired WebApplicationContext context;
 final JsonMapper json=JsonMapper.builder().build();final Actor actor=new Actor(UUID.randomUUID(),"Global historical actor");
 MockMvc mvc;UUID tenant,other,category,manufacturer,product,location,owner;Identity admin;
 @BeforeEach void setup(){clock.now=Instant.parse("2026-10-11T12:00:00Z");tenant=tenants.create("Tenant "+UUID.randomUUID(),actor);other=tenants.create("Other",actor);category=catalog.createCategory(null,"Category",actor);manufacturer=catalog.createManufacturer(null,"Manufacturer",actor);product=catalog.createProduct(null,category,manufacturer,"Product",null,actor);location=places.create(PlaceService.Kind.LOCATION,tenant,"Room",actor);owner=places.create(PlaceService.Kind.OWNER,tenant,"Owner",actor);admin=new Identity(actor.id(),Scope.GLOBAL,null,Role.ADMIN,Status.ACTIVE,actor.displayName());mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 AssetService.View asset(){return assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(product),location,owner,true,null,null,null),actor);}
 MvcResult request(Identity who,MockHttpServletRequestBuilder b,Object body)throws Exception{if(who!=null)b.header("Authorization","Bearer "+tokens.login(who).accessToken());if(body!=null)b.contentType("application/json").content(json.writeValueAsString(body));return mvc.perform(b).andReturn();}
 Map<String,Object> data(MvcResult r){return json.readValue(r.getResponse().getContentAsByteArray(),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});}
 int status(MvcResult r){return r.getResponse().getStatus();}
 long events(UUID id){return jdbc.queryForObject("SELECT count(*) FROM asset_event WHERE asset_id=?",Long.class,id);}
 UUID attribution(String action,UUID id){return jdbc.queryForObject("SELECT tenant_id FROM audit_log WHERE action=? AND target_id=? ORDER BY occurred_at DESC,id DESC LIMIT 1",UUID.class,action,id);}
 Identity principal(Scope scope,Role role,Status status){return new Identity(UUID.randomUUID(),scope,scope==Scope.TENANT?tenant:null,role,status,"Principal");}

 Map<String,Object> metadata(String table,UUID id){return jdbc.queryForMap("SELECT created_by,created_at,updated_by,updated_at FROM "+table+" WHERE "+(table.equals("tenant_oidc_config")?"tenant_id":"id")+"=?",id);}
 void created(String table,UUID id,UUID actorId){
  var m=metadata(table,id);assertEquals(actorId,m.get("created_by"));assertEquals(actorId,m.get("updated_by"));
  assertEquals(clock.instant(),((java.sql.Timestamp)m.get("created_at")).toInstant());assertEquals(m.get("created_at"),m.get("updated_at"));
 }
 void updated(String table,UUID id,Map<String,Object> before,UUID who){
  var m=metadata(table,id);assertEquals(before.get("created_by"),m.get("created_by"));assertEquals(before.get("created_at"),m.get("created_at"));
  assertEquals(who,m.get("updated_by"));assertEquals(clock.instant(),((java.sql.Timestamp)m.get("updated_at")).toInstant());assertNotEquals(before.get("updated_at"),m.get("updated_at"));
 }
 @Test void allBusinessCreationUsesAuthoritativeActorAndOneInstant(){
  created("tenant",tenant,actor.id());created("category",category,actor.id());created("manufacturer",manufacturer,actor.id());created("product",product,actor.id());
  created("location",location,actor.id());created("owner",owner,actor.id());created("asset",asset().id(),actor.id());
  configs.put(admin,tenant,"https://m1.example","client","secret",true,null,Set.of());created("tenant_oidc_config",tenant,actor.id());
  UUID internal=users.create(Scope.GLOBAL,null,Role.USER,Status.ACTIVE,"Infrastructure fixture");created("app_user",internal,SystemActor.ID);
 }
 @Test void systemSeedsAreRecognizableAndCannotBecomeUserOrCredential()throws Exception{
  for(var k:List.of(CatalogService.Kind.CATEGORY,CatalogService.Kind.MANUFACTURER)){
   UUID id=k==CatalogService.Kind.CATEGORY?CatalogService.UNKNOWN_CATEGORY:CatalogService.UNKNOWN_MANUFACTURER;
   var m=catalog.read(k,id).metadata();assertEquals(SystemActor.ID,m.createdBy());assertEquals(m.createdAt(),m.updatedAt());assertEquals(SystemActor.DISPLAY_NAME,m.createdByDisplayName());
   var r=request(admin,get("/api/v1/catalog/"+(k==CatalogService.Kind.CATEGORY?"categories":"manufacturers")+"/"+id),null);assertEquals(200,status(r));assertTrue(r.getResponse().getContentAsString().contains(SystemActor.DISPLAY_NAME));
  }
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id=?",Integer.class,SystemActor.ID));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO app_user(id,scope,role,status,display_name,created_by,created_at,updated_by,updated_at) VALUES(?,'GLOBAL','ADMIN','ACTIVE','Forged',?,now(),?,now())",SystemActor.ID,actor.id(),actor.id()));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO local_global_credential(user_id,email,password_hash,email_verified_at) VALUES(?,'system@example.org','hash',now())",SystemActor.ID));
 }
 @Test void bootstrapAdminCreationAndJitRetainTheirRealActors(){
  jdbc.update("UPDATE app_user SET status='DISABLED' WHERE scope='GLOBAL' AND role='ADMIN'");
  Identity boot=auth.bootstrap("m1-"+UUID.randomUUID()+"@example.org","Bootstrap","CorrectHorse25!Battery");created("app_user",boot.id(),boot.id());
  Identity u=auth.createGlobal(boot,"user-"+UUID.randomUUID()+"@example.org","Human","CorrectHorse25!Battery");created("app_user",u.id(),boot.id());
  UUID group=UUID.randomUUID();configs.put(boot,tenant,"https://jit.example","client","secret",true,null,Set.of(group));
  var pair=provisioning.complete(tenant,0,"https://jit.example","client","subject-"+UUID.randomUUID(),"JIT human",Set.of(group));created("app_user",pair.user().id(),pair.user().id());
 }
 @ParameterizedTest @EnumSource(CatalogService.Kind.class) void catalogEditPreservesCreation(CatalogService.Kind kind){
  String table=kind.name().toLowerCase();UUID id=switch(kind){case CATEGORY->catalog.createCategory(null,"Editable",actor);case MANUFACTURER->catalog.createManufacturer(null,"Editable",actor);case PRODUCT->product;};
  var before=metadata(table,id);Actor editor=new Actor(UUID.randomUUID(),"Editor");clock.now=clock.now.plusSeconds(10);
  if(kind==CatalogService.Kind.PRODUCT)catalog.editProduct(id,category,manufacturer,"Edited","number",editor);else catalog.editLabel(kind,id,"Edited",editor);
  updated(table,id,before,editor.id());
 }
 @ParameterizedTest @EnumSource(CatalogService.Kind.class) void publicationAndDeletionUpdateMetadata(CatalogService.Kind kind){
  UUID id=switch(kind){case CATEGORY->catalog.createCategory(tenant,"Local",actor);case MANUFACTURER->catalog.createManufacturer(tenant,"Local",actor);case PRODUCT->catalog.createProduct(tenant,category,manufacturer,"Local",null,actor);};
  String table=kind.name().toLowerCase();var before=metadata(table,id);Actor editor=new Actor(UUID.randomUUID(),"Editor");clock.now=clock.now.plusSeconds(10);catalog.publish(kind,id,editor);updated(table,id,before,editor.id());
  before=metadata(table,id);clock.now=clock.now.plusSeconds(10);catalog.delete(kind,id,actor);updated(table,id,before,actor.id());assertEquals(metadata(table,id).get("updated_at"),jdbc.queryForObject("SELECT deleted_at FROM "+table+" WHERE id=?",java.sql.Timestamp.class,id));
 }
 @ParameterizedTest @EnumSource(PlaceService.Kind.class) void noteCreateEditReadAndDeletion(PlaceService.Kind kind)throws Exception{
  String table=kind.name().toLowerCase();var created=request(admin,post("/api/v1/tenants/"+tenant+"/"+table+"s"),Map.of("name","Note resource","note","  Initial note  "));assertEquals(201,status(created));UUID id=UUID.fromString((String)data(created).get("id"));
  assertEquals("Initial note",places.read(kind,id).note());var before=metadata(table,id);clock.now=clock.now.plusSeconds(10);
  assertEquals(204,status(request(admin,put("/api/v1/"+table+"s/"+id),Map.of("name","Note resource","note"," Changed "))));updated(table,id,before,actor.id());
  assertEquals("Changed",data(request(admin,get("/api/v1/"+table+"s/"+id),null)).get("note"));
  assertEquals(204,status(request(admin,put("/api/v1/"+table+"s/"+id),Map.of("name","Note resource","note","  "))));assertNull(places.read(kind,id).note());
  assertEquals(204,status(request(admin,put("/api/v1/"+table+"s/"+id),Map.of("name","Note resource"))));assertNull(places.read(kind,id).note());
  clock.now=clock.now.plusSeconds(10);before=metadata(table,id);places.delete(kind,tenant,id,actor);updated(table,id,before,actor.id());
 }
 @Test void referencedLocationEditableButOwnerNameAndNoteBothBlocked(){
  asset();var before=metadata("location",location);clock.now=clock.now.plusSeconds(10);places.edit(PlaceService.Kind.LOCATION,tenant,location,"Room renamed","Note editable",actor);updated("location",location,before,actor.id());assertEquals("Note editable",places.read(PlaceService.Kind.LOCATION,location).note());
  var ownerBefore=metadata("owner",owner);for(var name:List.of("Changed","Owner")){
   var ex=assertThrows(BusinessFailure.class,()->places.edit(PlaceService.Kind.OWNER,tenant,owner,name,"Note-only also rejected",actor));assertEquals("REFERENCED",ex.code());
  }assertEquals(ownerBefore,metadata("owner",owner));assertNull(places.read(PlaceService.Kind.OWNER,owner).note());
 }
 @ParameterizedTest @EnumSource(PlaceService.Kind.class) void noteIsNotSearchFilterOrSort(PlaceService.Kind kind)throws Exception{
  String unique="note-only-"+UUID.randomUUID();UUID id=places.create(kind,tenant,"Readable",unique,actor);
  assertTrue(places.list(kind,tenant,Map.of("q",unique)).items().isEmpty());assertEquals(unique,places.read(kind,id).note());
  String path="/api/v1/tenants/"+tenant+"/"+kind.name().toLowerCase()+"s";
  assertEquals(400,status(request(admin,get(path+"?note=x"),null)));assertEquals(400,status(request(admin,get(path+"?sort=note"),null)));
 }
 @Test void ownerAndLocationForeignTenantCannotBeMutated()throws Exception{
  var local=new Identity(UUID.randomUUID(),Scope.TENANT,other,Role.ADMIN,Status.ACTIVE,"Foreign");
  for(String table:List.of("locations","owners")){
   UUID id=table.equals("locations")?location:owner;var kind=table.equals("locations")?PlaceService.Kind.LOCATION:PlaceService.Kind.OWNER;
   var protectedRow=places.read(kind,id);String sqlTable=kind.name().toLowerCase();long protectedCount=jdbc.queryForObject("SELECT count(*) FROM "+sqlTable+" WHERE tenant_id=?",Long.class,tenant);
   assertEquals(404,status(request(local,put("/api/v1/"+table+"/"+id),Map.of("name","Forged","note","forged"))));
   assertEquals(404,status(request(local,delete("/api/v1/"+table+"/"+id),null)));
   // Transport Tenant is ignored for TENANT principals; only the signed own Tenant can receive the new row.
   var created=request(local,post("/api/v1/tenants/"+tenant+"/"+table),Map.of("name","Own Tenant creation"));assertEquals(201,status(created));
   UUID createdId=UUID.fromString((String)data(created).get("id"));var createdRow=places.read(kind,createdId);
   assertEquals(other,createdRow.tenantId());assertEquals(local.id(),createdRow.metadata().createdBy());assertEquals(local.id(),createdRow.metadata().updatedBy());assertEquals(other,attribution(kind+"_CREATED",createdId));
   assertEquals(protectedRow,places.read(kind,id));assertEquals(protectedCount,jdbc.queryForObject("SELECT count(*) FROM "+sqlTable+" WHERE tenant_id=?",Long.class,tenant));
  }
 }
 @Test void mergeUpdatesRedirectedProductAndAssetAndSourceMetadataAtomically(){
  var a=asset();UUID target=catalog.createCategory(null,"Target",actor);var pBefore=metadata("product",product);var aBefore=metadata("asset",a.id());var cBefore=metadata("category",category);
  Actor editor=new Actor(UUID.randomUUID(),"Merge editor");clock.now=clock.now.plusSeconds(10);merges.merge(CatalogService.MergeKind.CATEGORY,category,target,editor);
  updated("product",product,pBefore,editor.id());updated("asset",a.id(),aBefore,editor.id());updated("category",category,cBefore,editor.id());assertEquals(2,events(a.id()));
 }
 @Test void auditFailureRollsBackCreateAndMetadataUpdate(){
  var before=metadata("location",location);clock.now=clock.now.plusSeconds(10);
  jdbc.execute("CREATE FUNCTION m1_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action IN ('LOCATION_EDITED','LOCATION_CREATED') THEN RAISE EXCEPTION 'isolated audit failure'; END IF; RETURN NEW; END; $$");
  jdbc.execute("CREATE TRIGGER m1_reject_audit BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION m1_reject_audit()");
  long count=jdbc.queryForObject("SELECT count(*) FROM location",Long.class),audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
  try{
   assertThrows(RuntimeException.class,()->places.edit(PlaceService.Kind.LOCATION,tenant,location,"Rollback","lost",actor));assertEquals(before,metadata("location",location));assertEquals("Room",places.read(PlaceService.Kind.LOCATION,location).name());
   assertThrows(RuntimeException.class,()->places.create(PlaceService.Kind.LOCATION,tenant,"Rollback","lost",actor));assertEquals(count,jdbc.queryForObject("SELECT count(*) FROM location",Long.class));assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  }finally{jdbc.execute("DROP TRIGGER m1_reject_audit ON audit_log");jdbc.execute("DROP FUNCTION m1_reject_audit()");}
 }
 @Test void eventFailureRollsBackAssetMetadataAndHistory(){
  var a=asset();var before=metadata("asset",a.id());clock.now=clock.now.plusSeconds(10);
  jdbc.execute("CREATE FUNCTION m1_reject_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.type='UPDATED' THEN RAISE EXCEPTION 'isolated event failure'; END IF; RETURN NEW; END; $$");jdbc.execute("CREATE TRIGGER m1_reject_event BEFORE INSERT ON asset_event FOR EACH ROW EXECUTE FUNCTION m1_reject_event()");
  try{assertThrows(RuntimeException.class,()->assets.editText(tenant,a.id(),0,null,null,"Rollback",actor));assertEquals(before,metadata("asset",a.id()));assertEquals(1,events(a.id()));assertNull(assets.get(tenant,a.id()).note());}
  finally{jdbc.execute("DROP TRIGGER m1_reject_event ON asset_event");jdbc.execute("DROP FUNCTION m1_reject_event()");}
 }
 @Test void metadataOverpostingRejectedForCatalogPlacesAndAssets()throws Exception{
  for(String key:List.of("createdBy","createdAt","updatedBy","updatedAt")){
   for(String kind:List.of("categories","manufacturers","products","locations","owners","assets")){
    String path="/api/v1/tenants/"+tenant+"/"+(Set.of("categories","manufacturers","products").contains(kind)?"catalog/":"")+kind;
    var body=new LinkedHashMap<String,Object>();body.put("name","Overpost");body.put(key,UUID.randomUUID().toString());assertEquals(400,status(request(admin,post(path),body)),kind+" "+key);
   }
  }
 }
 @Test void cleanMigrationSeedsAreNewAndPopulatedMigrationFailsWithoutChangingData(){
  String schema="m1_"+UUID.randomUUID().toString().replace("-","");
  var old=org.flywaydb.core.Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas(schema).defaultSchema(schema).target("6").load();old.migrate();
  UUID id=UUID.randomUUID();jdbc.update("INSERT INTO "+schema+".tenant(id,name) VALUES(?,'Existing no provenance')",id);var checksum=jdbc.queryForList("SELECT checksum FROM "+schema+".flyway_schema_history ORDER BY installed_rank",Integer.class);
  var latest=org.flywaydb.core.Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas(schema).defaultSchema(schema).load();var ex=assertThrows(RuntimeException.class,latest::migrate);assertTrue(ex.getMessage().contains("M1 requires a clean business state"));
  assertEquals("Existing no provenance",jdbc.queryForObject("SELECT name FROM "+schema+".tenant WHERE id=?",String.class,id));assertEquals(checksum,jdbc.queryForList("SELECT checksum FROM "+schema+".flyway_schema_history ORDER BY installed_rank",Integer.class));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND table_name='tenant' AND column_name='created_by'",Integer.class,schema));
  // Only isolated test data is cleared to verify the migration's clean-state contract.
  jdbc.update("DELETE FROM "+schema+".tenant WHERE id=?",id);Instant lower=jdbc.queryForObject("SELECT clock_timestamp()",java.sql.Timestamp.class).toInstant();latest.migrate();latest.validate();
  var seed=jdbc.queryForMap("SELECT created_by,created_at,updated_at FROM "+schema+".category WHERE name='UNKNOWN'");assertEquals(SystemActor.ID,seed.get("created_by"));assertFalse(((java.sql.Timestamp)seed.get("created_at")).toInstant().isBefore(lower));assertEquals(seed.get("created_at"),seed.get("updated_at"));
 }
 @Test void providerAndUserUpdatesPreserveCreationAndUseRealAdministrator(){
  configs.put(admin,tenant,"https://m1.example","client","secret",true,null,Set.of());var pBefore=metadata("tenant_oidc_config",tenant);
  UUID id=users.create(Scope.GLOBAL,null,Role.USER,Status.ACTIVE,"User");var uBefore=metadata("app_user",id);clock.now=clock.now.plusSeconds(10);
  configs.put(admin,tenant,"https://m1.example","client",null,false,0L,Set.of(UUID.randomUUID()));updated("tenant_oidc_config",tenant,pBefore,actor.id());
  auth.role(admin,id,Role.ADMIN);updated("app_user",id,uBefore,actor.id());uBefore=metadata("app_user",id);clock.now=clock.now.plusSeconds(10);auth.resetCredential(admin,id,"reset-"+UUID.randomUUID()+"@example.org","CorrectHorse25!Battery");updated("app_user",id,uBefore,actor.id());
 }
 @ParameterizedTest @ValueSource(strings={"approve","disable","own-password"}) void userMutationMetadata(String operation){
  UUID id;Identity real;
  if(operation.equals("own-password")){real=auth.createGlobal(admin,"own-"+UUID.randomUUID()+"@example.org","Password owner","CorrectHorse25!Battery");id=real.id();}
  else{id=users.create(Scope.TENANT,tenant,Role.USER,operation.equals("approve")?Status.PENDING:Status.ACTIVE,"Managed");real=admin;}
  var before=metadata("app_user",id);clock.now=clock.now.plusSeconds(10);
  switch(operation){case "approve"->auth.approve(admin,id);case "disable"->auth.disable(admin,id);case "own-password"->auth.ownPassword(real,"CorrectHorse25!Battery","Replacement25!Horse");}
  updated("app_user",id,before,real.id());
 }
 @Test void mergeAuditFailureRollsBackAllMetadataAndHistory(){
  var a=asset();UUID target=catalog.createCategory(null,"Target",actor);var p=metadata("product",product);var c=metadata("category",category);var v=metadata("asset",a.id());clock.now=clock.now.plusSeconds(10);
  jdbc.execute("CREATE FUNCTION m1_reject_merge() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='CATEGORY_MERGED' THEN RAISE EXCEPTION 'isolated merge audit failure'; END IF; RETURN NEW; END; $$");jdbc.execute("CREATE TRIGGER m1_reject_merge BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION m1_reject_merge()");
  try{assertThrows(RuntimeException.class,()->merges.merge(CatalogService.MergeKind.CATEGORY,category,target,actor));assertEquals(p,metadata("product",product));assertEquals(c,metadata("category",category));assertEquals(v,metadata("asset",a.id()));assertEquals(1,events(a.id()));assertFalse(catalog.read(CatalogService.Kind.CATEGORY,category).deleted());assertEquals(category,assets.get(tenant,a.id()).categoryId());}
  finally{jdbc.execute("DROP TRIGGER m1_reject_merge ON audit_log");jdbc.execute("DROP FUNCTION m1_reject_merge()");}
 }

 @Test void adminTransportCannotOwnMetadata()throws Exception{
  var forbidden=Map.of("createdBy",UUID.randomUUID().toString(),"updatedBy",UUID.randomUUID().toString(),"createdAt","2001-01-01T00:00:00Z","updatedAt","2001-01-01T00:00:00Z");
  var b=new LinkedHashMap<String,Object>(forbidden);b.put("name","Transport Tenant");var r=request(admin,post("/api/v1/tenants"),b);
  // Typed administrative DTOs may reject unknown fields or ignore them; neither can persist them.
  if(status(r)!=400){assertEquals(200,status(r));created("tenant",UUID.fromString(json.readValue(r.getResponse().getContentAsByteArray(),String.class)),actor.id());}
  b=new LinkedHashMap<>(forbidden);b.put("email","transport-"+UUID.randomUUID()+"@example.org");b.put("displayName","Transport User");b.put("password","CorrectHorse25!Battery");r=request(admin,post("/api/v1/users/global"),b);
  if(status(r)!=400){assertEquals(200,status(r));created("app_user",UUID.fromString((String)data(r).get("id")),actor.id());}
  b=new LinkedHashMap<>(forbidden);b.put("issuer","https://transport.example");b.put("clientId","client");b.put("clientSecret","secret");b.put("enabled",true);b.put("allowedGroupIds",List.of());r=request(admin,put("/api/v1/tenants/"+tenant+"/oidc"),b);
  if(status(r)!=400){assertEquals(200,status(r));created("tenant_oidc_config",tenant,actor.id());}
 }

}
