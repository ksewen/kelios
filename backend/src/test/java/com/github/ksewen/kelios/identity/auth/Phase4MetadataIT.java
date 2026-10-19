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
class Phase4MetadataIT {
 @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17-alpine");
 static final AuthTestKeys KEYS;static{try{KEYS=new AuthTestKeys("phase4-it-keys");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",PG::getJdbcUrl);p.add("spring.datasource.username",PG::getUsername);p.add("spring.datasource.password",PG::getPassword);p.add("kelios.auth.key-directory",()->KEYS.directory.toAbsolutePath().toString());p.add("kelios.auth.public-origin",()->"https://kelios.example");p.add("kelios.auth.issuer",()->"https://kelios.example");}
 @Autowired AssetService assets;@Autowired CatalogService catalog;@Autowired PlaceService places;@Autowired CatalogMergeService merges;
 @Autowired TenantService tenants;@Autowired UserService users;@Autowired IdentityStore identities;@Autowired AuthenticationService auth;
 @Autowired OidcConfigurationService configs;@Autowired OidcProvisioning provisioning;@Autowired JwtTokens tokens;
 @Autowired SecretEnvelope envelope;
 @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager tm;@Autowired WebApplicationContext context;
 final JsonMapper json=JsonMapper.builder().build();final Actor actor=new Actor(UUID.randomUUID(),"Global historical actor");
 MockMvc mvc;UUID tenant,other,category,manufacturer,product,location,owner;Identity admin;
 @BeforeEach void setup(){tenant=tenants.create("Tenant "+UUID.randomUUID(),actor);other=tenants.create("Other",actor);category=catalog.createCategory(null,"Category",actor);manufacturer=catalog.createManufacturer(null,"Manufacturer",actor);product=catalog.createProduct(null,category,manufacturer,"Product",null,actor);location=places.create(PlaceService.Kind.LOCATION,tenant,"Room",actor);owner=places.create(PlaceService.Kind.OWNER,tenant,"Owner",actor);admin=new Identity(actor.id(),Scope.GLOBAL,null,Role.ADMIN,Status.ACTIVE,actor.displayName());mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 AssetService.View asset(){return assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(product),location,owner,true,null,null,null),actor);}
 MvcResult request(Identity who,MockHttpServletRequestBuilder b,Object body)throws Exception{if(who!=null)b.header("Authorization","Bearer "+tokens.login(who).accessToken());if(body!=null)b.contentType("application/json").content(json.writeValueAsString(body));return mvc.perform(b).andReturn();}
 Map<String,Object> data(MvcResult r){return json.readValue(r.getResponse().getContentAsByteArray(),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});}
 int status(MvcResult r){return r.getResponse().getStatus();}
 long events(UUID id){return jdbc.queryForObject("SELECT count(*) FROM asset_event WHERE asset_id=?",Long.class,id);}
 UUID attribution(String action,UUID id){return jdbc.queryForObject("SELECT tenant_id FROM audit_log WHERE action=? AND target_id=? ORDER BY occurred_at DESC,id DESC LIMIT 1",UUID.class,action,id);}
 Identity principal(Scope scope,Role role,Status status){return new Identity(UUID.randomUUID(),scope,scope==Scope.TENANT?tenant:null,role,status,"Principal");}
 @Test void optionalRegistrationReplacementAndIndependentIdentifiers()throws Exception{
  var b=new LinkedHashMap<String,Object>();b.put("productId",product);b.put("locationId",location);b.put("loanable",false);b.put("localIdentifier","  Lehrer-NV  ");b.put("serialNumber","serial");b.put("externalInventoryNumber","external");
  var r=request(admin,post("/api/v1/tenants/"+tenant+"/assets"),b);assertEquals(201,status(r));UUID id=UUID.fromString((String)data(r).get("id"));assertEquals("Lehrer-NV",data(r).get("localIdentifier"));assertEquals("Lehrer-NV",snapshot(id,"REGISTERED","after"));
  long audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
  var edited=request(admin,put("/api/v1/assets/"+id+"/metadata"),Map.of("expectedVersion",0,"localIdentifier"," R421-PC7 ","note","note"));assertEquals(200,status(edited));assertEquals("R421-PC7",data(edited).get("localIdentifier"));assertNull(data(edited).get("serialNumber"));assertNull(data(edited).get("externalInventoryNumber"));assertEquals("Lehrer-NV",snapshot(id,"UPDATED","before"));assertEquals("R421-PC7",snapshot(id,"UPDATED","after"));assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));assertEquals(data(r).get("assetTag"),data(edited).get("assetTag"));
  var clear=request(admin,put("/api/v1/assets/"+id+"/metadata"),Map.of("expectedVersion",1));assertEquals(200,status(clear));assertNull(data(clear).get("localIdentifier"));
  var nullable=new LinkedHashMap<String,Object>();nullable.put("expectedVersion",2);nullable.put("localIdentifier",null);assertEquals(200,status(request(admin,put("/api/v1/assets/"+id+"/metadata"),nullable)));
  b.remove("productId");b.put("categoryId",category);b.put("manufacturerId",manufacturer);b.remove("localIdentifier");assertNull(data(request(admin,post("/api/v1/tenants/"+tenant+"/assets"),b)).get("localIdentifier"));
 }
 String snapshot(UUID id,String type,String side){return jdbc.queryForObject("SELECT data #>> ARRAY[?,'localIdentifier'] FROM asset_event WHERE asset_id=? AND type=? ORDER BY occurred_at DESC,id DESC LIMIT 1",String.class,side,id,type);}
 static java.util.stream.Stream<Arguments> values(){return java.util.stream.Stream.of(Arguments.of("\u00a0","\u00a0",true),Arguments.of("  MiXeD  text  ","MiXeD  text",true),Arguments.of(" ",null,true),Arguments.of("Äö-设备 😀", "Äö-设备 😀",true),Arguments.of("😀".repeat(128),"😀".repeat(128),true),Arguments.of("😀".repeat(129),null,false),Arguments.of("a\u0000b",null,false),Arguments.of("a\u0001b",null,false),Arguments.of("a\u007fb",null,false),Arguments.of("a\u0085b",null,false),Arguments.of("a\u009fb",null,false),Arguments.of("a\u2028b",null,false),Arguments.of("a\u2029b",null,false),Arguments.of("<b>label</b>","<b>label</b>",true));}
 @ParameterizedTest @MethodSource("values") void codePointsNormalizationAndDatabaseCharacters(String input,String normalized,boolean valid)throws Exception{
  var b=Map.of("productId",product,"locationId",location,"loanable",false,"localIdentifier",input);long rows=jdbc.queryForObject("SELECT count(*) FROM asset",Long.class);var r=request(admin,post("/api/v1/tenants/"+tenant+"/assets"),b);assertEquals(valid?201:400,status(r),r.getResponse().getContentAsString());if(valid){assertEquals(normalized,data(r).get("localIdentifier"));assertEquals(normalized,jdbc.queryForObject("SELECT local_identifier FROM asset WHERE id=?",String.class,UUID.fromString((String)data(r).get("id"))));}else{assertEquals("INVALID_INPUT",data(r).get("code"));assertEquals(rows,jdbc.queryForObject("SELECT count(*) FROM asset",Long.class));}
  if(valid && input.startsWith("Äö")){var searched=request(admin,get("/api/v1/tenants/"+tenant+"/assets").param("q","äÖ-设备").param("productId",product.toString()),null);assertEquals(200,status(searched));assertEquals(1L,((Number)data(searched).get("totalElements")).longValue());assertTrue(searched.getResponse().getContentAsString().contains((String)data(r).get("id")));}
  if(!valid && !input.contains("\u0000")){var a=asset();assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE asset SET local_identifier=? WHERE id=?",input,a.id()));assertNull(assets.get(tenant,a.id()).localIdentifier());}
 }
 @Test void duplicateRetentionTransferSearchAndIsolation()throws Exception{
  var first=assets.register(tenant,new AssetService.Registration(new AssetService.DirectSelection(category,manufacturer),location,null,false,null,"independent",null,"Ab%_\\Code"),actor);
  var same=assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(product),location,null,false,null,null,null,"Ab%_\\Code"),actor);
  UUID otherLoc=places.create(PlaceService.Kind.LOCATION,other,"Other room",actor);var foreign=assets.register(other,new AssetService.Registration(new AssetService.ProductSelection(product),otherLoc,null,false,null,null,null,"Ab%_\\Code"),actor);
  var search=request(principal(Scope.TENANT,Role.USER,Status.ACTIVE),get("/api/v1/tenants/"+tenant+"/assets").param("q","ab%_\\code"),null);assertEquals(200,status(search));assertEquals(2L,((Number)data(search).get("totalElements")).longValue());assertFalse(search.getResponse().getContentAsString().contains(foreign.id().toString()));
  assertEquals(3L,((Number)data(request(admin,get("/api/v1/assets").param("q","ab%_\\code"),null)).get("totalElements")).longValue());
  var dereg=assets.lifecycle(tenant,first.id(),0,LifecycleState.DEREGISTERED,DeregistrationReason.TRANSFER,null,actor);assertEquals("Ab%_\\Code",dereg.localIdentifier());var edit=assets.editText(tenant,first.id(),1,null,null,null,"New",actor);assertEquals("New",edit.localIdentifier());assets.delete(tenant,same.id(),0,actor);assertEquals("Ab%_\\Code",assets.get(tenant,same.id()).localIdentifier());
  assertNull(assets.register(other,new AssetService.Registration(new AssetService.ProductSelection(product),otherLoc,null,false,null,null,null),actor).localIdentifier());
  assertEquals(403,status(request(principal(Scope.TENANT,Role.USER,Status.ACTIVE),put("/api/v1/assets/"+first.id()+"/metadata"),Map.of("expectedVersion",2,"localIdentifier","forged"))));
  assertEquals(403,status(request(principal(Scope.TENANT,Role.ADMIN,Status.PENDING),get("/api/v1/tenants/"+tenant+"/assets"),null)));
  assertEquals(404,status(request(principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE),get("/api/v1/assets/"+foreign.id()),null)));
  var filtered=request(admin,get("/api/v1/assets").param("localIdentifier","ab%_\\code"),null);assertEquals(200,status(filtered));assertEquals(1L,((Number)data(filtered).get("totalElements")).longValue());assertFalse(filtered.getResponse().getContentAsString().contains(same.id().toString()));assertEquals(400,status(request(admin,get("/api/v1/assets").param("sort","localIdentifier"),null)));
 }
 @Test void snapshotsOldHistoryRollbackAndEveryWorkflow(){
  var a=assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(product),location,null,false,null,null,null,"Stable"),actor);
  jdbc.execute("CREATE FUNCTION phase4_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$");jdbc.execute("CREATE TRIGGER phase4_fail BEFORE INSERT ON asset_event FOR EACH ROW EXECUTE FUNCTION phase4_fail()");
  try{assertThrows(Exception.class,()->assets.editText(tenant,a.id(),0,null,null,null,"Uncommitted",actor));}finally{jdbc.execute("DROP TRIGGER phase4_fail ON asset_event");jdbc.execute("DROP FUNCTION phase4_fail()");}
  assertEquals("Stable",assets.get(tenant,a.id()).localIdentifier());assertEquals(0,assets.get(tenant,a.id()).version());assertEquals(1,events(a.id()));
  assets.condition(tenant,a.id(),0,ConditionState.WORKING,null,actor);assets.lifecycle(tenant,a.id(),1,LifecycleState.DEPLOYED,null,null,actor);assets.changeLoanability(tenant,a.id(),2,true,actor);assets.loan(tenant,a.id(),3,"Borrower",null,null,actor);assets.returned(tenant,a.id(),4,actor);
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM asset_event WHERE asset_id=? AND NOT jsonb_exists(data->'after','localIdentifier')",Integer.class,a.id()));
 }
 @Test void populatedV5UpgradePreservesAllDataAndFreshSchema(){
  UUID global=users.create(Scope.GLOBAL,null,Role.USER,Status.ACTIVE,"Migration global"),local=users.create(Scope.TENANT,tenant,Role.USER,Status.PENDING,"Migration tenant");
  jdbc.update("INSERT INTO local_global_credential(user_id,email,password_hash,email_verified_at) VALUES(?,?,'fixture-hash',now())",global,"migration-"+global.toString().substring(0,8)+"@example.org");
  configs.put(admin,tenant,"https://fixture.example","client","private test fixture",true,null,Set.of(UUID.randomUUID()));jdbc.update("INSERT INTO external_identity(issuer,subject,user_id,tenant_id) VALUES('https://fixture.example',?,?,?)",local.toString(),local,tenant);
  var a=asset();String schema="phase4_"+UUID.randomUUID().toString().replace("-","");var old=org.flywaydb.core.Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas(schema).defaultSchema(schema).target("5").load();old.migrate();
  var tables=List.of("tenant","app_user","category","manufacturer","product","location","owner","asset","asset_event","asset_tag_counter","local_global_credential","tenant_oidc_config","tenant_oidc_allowed_group","external_identity","global_admin_recovery_guard","audit_log");var before=new LinkedHashMap<String,List<String>>();
  for(String table:tables){String columns=String.join(",",jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name=? ORDER BY ordinal_position",String.class,schema,table));jdbc.execute("INSERT INTO "+schema+"."+table+" SELECT "+(table.equals("local_global_credential")?columns.replace("login_name","split_part(email,'@',1)"):columns)+" FROM public."+table+" ON CONFLICT DO NOTHING");before.put(table,jdbc.queryForList("SELECT to_jsonb(t)::text FROM "+schema+"."+table+" t ORDER BY to_jsonb(t)::text",String.class));}
  var checksums=jdbc.queryForList("SELECT checksum FROM "+schema+".flyway_schema_history ORDER BY installed_rank",Integer.class);var latest=org.flywaydb.core.Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas(schema).defaultSchema(schema).target("6").load();latest.migrate();latest.validate();assertEquals(checksums,jdbc.queryForList("SELECT checksum FROM "+schema+".flyway_schema_history WHERE version IS NULL OR version<>'6' ORDER BY installed_rank",Integer.class));
  for(String table:tables)assertEquals(before.get(table),jdbc.queryForList("SELECT "+(table.equals("asset")?"(to_jsonb(t)-'local_identifier')":"to_jsonb(t)")+"::text FROM "+schema+"."+table+" t ORDER BY "+(table.equals("asset")?"(to_jsonb(t)-'local_identifier')":"to_jsonb(t)")+"::text",String.class),table);
  assertNull(jdbc.queryForObject("SELECT local_identifier FROM "+schema+".asset WHERE id=?",String.class,a.id()));assertEquals(9,jdbc.queryForObject("SELECT count(*) FROM public.flyway_schema_history WHERE success",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='asset' AND indexdef LIKE '%local_identifier%'",Integer.class));
 }

 @Test void creatorMigrationLeavesPreexistingRowsUnattributed(){
  String schema="creator_"+UUID.randomUUID().toString().replace("-","");
  var before=org.flywaydb.core.Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas(schema).defaultSchema(schema).target("8").load();before.migrate();
  UUID legacy=UUID.randomUUID();jdbc.update("INSERT INTO "+schema+".tenant(id,name,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,now(),?,now())",legacy,"Legacy",actor.id(),actor.id());
  var after=org.flywaydb.core.Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas(schema).defaultSchema(schema).target("9").load();after.migrate();after.validate();
  for(String table:List.of("tenant","app_user","category","manufacturer","product","asset","location","owner"))assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM "+schema+"."+table+" WHERE created_by_display_name IS NOT NULL",Integer.class),table);
  assertNull(jdbc.queryForObject("SELECT created_by_display_name FROM "+schema+".tenant WHERE id=?",String.class,legacy));
 }

 @Test void creationSnapshotsCoverEightResourcesAndDoNotFollowLaterActorNames()throws Exception{
  var a=asset();UUID systemUser=users.create(Scope.GLOBAL,null,Role.USER,Status.ACTIVE,"Internal user");
  for(var entry:Map.of("tenant",tenant,"category",category,"manufacturer",manufacturer,"product",product,"asset",a.id(),"location",location,"owner",owner).entrySet()){
   assertEquals(actor.displayName(),jdbc.queryForObject("SELECT created_by_display_name FROM "+entry.getKey()+" WHERE id=?",String.class,entry.getValue()),entry.getKey());
  }
  assertEquals(com.github.ksewen.kelios.audit.SystemActor.DISPLAY_NAME,jdbc.queryForObject("SELECT created_by_display_name FROM app_user WHERE id=?",String.class,systemUser));
  assertEquals(actor.displayName(),data(request(admin,get("/api/v1/assets/"+a.id()),null)).get("createdByDisplayName"));
  jdbc.update("UPDATE asset SET updated_by=?,updated_at=now() WHERE id=?",UUID.randomUUID(),a.id());
  assertEquals(actor.displayName(),jdbc.queryForObject("SELECT created_by_display_name FROM asset WHERE id=?",String.class,a.id()));
 }

 @Test void oidcJitSnapshotsOnlyAnAvailableProviderDisplayLabel(){
  UUID group=UUID.randomUUID();configs.put(admin,tenant,"https://jit.example","client","fixture secret",true,null,Set.of(group));
  var named=provisioning.complete(tenant,0,"https://jit.example","client","named-subject","Provider Person",Set.of(group));
  var unnamed=provisioning.complete(tenant,0,"https://jit.example","client","unnamed-subject",null,Set.of(group));
  assertEquals("Provider Person",jdbc.queryForObject("SELECT created_by_display_name FROM app_user WHERE id=?",String.class,named.user().id()));
  assertNull(jdbc.queryForObject("SELECT created_by_display_name FROM app_user WHERE id=?",String.class,unnamed.user().id()));
 }

 @Test void oldImmutableSnapshotAbsenceIsNotInferred() {
  var a=asset();UUID old=UUID.randomUUID();jdbc.update("INSERT INTO asset_event(id,asset_id,tenant_id,type,occurred_at,created_at,actor_id,actor_display_name,data) VALUES(?,?,?,'UPDATED',now(),now(),?,?,'{\"before\":{},\"after\":{\"note\":\"historical\"}}'::jsonb)",old,a.id(),tenant,actor.id(),actor.displayName());
  String before=jdbc.queryForObject("SELECT data::text FROM asset_event WHERE id=?",String.class,old);assets.editText(tenant,a.id(),0,null,null,null,"New label",actor);assertEquals(before,jdbc.queryForObject("SELECT data::text FROM asset_event WHERE id=?",String.class,old));assertFalse(jdbc.queryForObject("SELECT jsonb_exists(data->'after','localIdentifier') FROM asset_event WHERE id=?",Boolean.class,old));
 }
 @Test void disposableBackupRestorePreservesDataAndRequiresExternalEncryptionKey()throws Exception {
  var a=assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(product),location,owner,false,null,null,null,"Restore label"),actor);
  configs.put(admin,tenant,"https://fixture.example","restored-client","private restore fixture",true,null,Set.of(UUID.randomUUID()));
  UUID user=users.create(Scope.GLOBAL,null,Role.USER,Status.ACTIVE,"Restored identity");jdbc.update("INSERT INTO local_global_credential(user_id,email,password_hash,email_verified_at) VALUES(?,?,'fixture-hash',now())",user,"restore-"+user.toString().substring(0,8)+"@example.org");
  assertEquals(0,PG.execInContainer("pg_dump","-U",PG.getUsername(),"-d",PG.getDatabaseName(),"-Fc","-f","/tmp/phase4-restore.dump").getExitCode());assertEquals(0,PG.execInContainer("createdb","-U",PG.getUsername(),"phase4_restore").getExitCode());assertEquals(0,PG.execInContainer("pg_restore","-U",PG.getUsername(),"-d","phase4_restore","--no-owner","/tmp/phase4-restore.dump").getExitCode());
  var restored=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(PG.getJdbcUrl().replace("/"+PG.getDatabaseName(),"/phase4_restore"),PG.getUsername(),PG.getPassword()));
  for(String table:List.of("tenant","app_user","asset","asset_event","category","manufacturer","product","location","owner","audit_log","asset_tag_counter","local_global_credential","tenant_oidc_config","tenant_oidc_allowed_group","external_identity","global_admin_recovery_guard","flyway_schema_history"))assertEquals(jdbc.queryForList("SELECT to_jsonb(t)::text FROM "+table+" t ORDER BY to_jsonb(t)::text",String.class),restored.queryForList("SELECT to_jsonb(t)::text FROM "+table+" t ORDER BY to_jsonb(t)::text",String.class),table);
  assertEquals("Restore label",restored.queryForObject("SELECT local_identifier FROM asset WHERE id=?",String.class,a.id()));String encrypted=restored.queryForObject("SELECT encrypted_client_secret FROM tenant_oidc_config WHERE tenant_id=?",String.class,tenant);assertEquals("private restore fixture",envelope.decrypt(encrypted,OidcConfigurationService.context(tenant,"https://fixture.example","restored-client")));
  java.nio.file.Path key=KEYS.directory.resolve("main.aes"),missing=KEYS.directory.resolve("main.aes.unavailable");java.nio.file.Files.move(key,missing);try{var failure=assertThrows(AuthFailure.class,()->envelope.decrypt(encrypted,OidcConfigurationService.context(tenant,"https://fixture.example","restored-client")));assertEquals(503,failure.status());}finally{java.nio.file.Files.move(missing,key);}
  assertEquals("private restore fixture",envelope.decrypt(encrypted,OidcConfigurationService.context(tenant,"https://fixture.example","restored-client")));
 }
 @ParameterizedTest @ValueSource(strings={"metadata","merge","metadataThenMerge"}) void independentConnectionsMetadataVersusMergeHasOneWinner(String operation)throws Exception{
  var a=asset();UUID target=catalog.createCategory(null,"Target",actor);var pool=Executors.newFixedThreadPool(2);var held=new CountDownLatch(1);var release=new CountDownLatch(1);
  try{var first=pool.submit(()->new TransactionTemplate(tm).execute(s->{if(operation.equals("merge"))merges.merge(CatalogService.MergeKind.CATEGORY,category,target,actor);else assets.editText(tenant,a.id(),0,null,null,null,"Winner",actor);held.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return null;}));assertTrue(held.await(10,TimeUnit.SECONDS));String connection="phase4-"+UUID.randomUUID();var second=pool.submit(()->new TransactionTemplate(tm).execute(st->{jdbc.queryForObject("SELECT set_config('application_name',?,true)",String.class,connection);if(operation.equals("metadataThenMerge")){merges.merge(CatalogService.MergeKind.CATEGORY,category,target,actor);return null;}return assets.editText(tenant,a.id(),0,null,null,null,"Loser",actor);}));try{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean blocked=false;while(System.nanoTime()<deadline){if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')",Boolean.class,connection)){blocked=true;break;}Thread.sleep(20);}assertTrue(blocked,"Actual PostgreSQL lock wait");}finally{release.countDown();}first.get(15,TimeUnit.SECONDS);if(operation.equals("metadataThenMerge"))second.get(15,TimeUnit.SECONDS);else{var ex=assertThrows(ExecutionException.class,()->second.get(15,TimeUnit.SECONDS));assertInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class,ex.getCause());}assertEquals(!operation.equals("merge")?"Winner":null,assets.get(tenant,a.id()).localIdentifier());assertEquals(operation.equals("metadataThenMerge")?3:2,events(a.id()));assertEquals(!operation.equals("merge")?"Winner":null,snapshot(a.id(),"UPDATED","after"));}finally{release.countDown();pool.shutdownNow();}
 }
}
