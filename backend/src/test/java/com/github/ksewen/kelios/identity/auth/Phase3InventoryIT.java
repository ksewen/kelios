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
class Phase3InventoryIT {
 @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17-alpine");
 static final AuthTestKeys KEYS;static{try{KEYS=new AuthTestKeys("phase3-it-keys");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",PG::getJdbcUrl);p.add("spring.datasource.username",PG::getUsername);p.add("spring.datasource.password",PG::getPassword);p.add("kelios.auth.key-directory",()->KEYS.directory.toAbsolutePath().toString());p.add("kelios.auth.public-origin",()->"https://kelios.example");p.add("kelios.auth.issuer",()->"https://kelios.example");}
 @Autowired AssetService assets;@Autowired CatalogService catalog;@Autowired PlaceService places;@Autowired CatalogMergeService merges;
 @Autowired TenantService tenants;@Autowired UserService users;@Autowired IdentityStore identities;@Autowired AuthenticationService auth;
 @Autowired OidcConfigurationService configs;@Autowired OidcProvisioning provisioning;@Autowired JwtTokens tokens;
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
 static java.util.stream.Stream<Arguments> graphCases(){var cases=new ArrayList<Arguments>();for(boolean loan:List.of(false,true))for(String kind:List.of("lifecycle","condition"))for(int from=0;from<4;from++)for(int to=0;to<4;to++)cases.add(Arguments.of(kind,from,to,loan));return cases.stream();}
 @ParameterizedTest @MethodSource("graphCases") void allTransitionPairsAndLoanIndependence(String kind,int from,int to,boolean loan)throws Exception{
  var a=asset();String[] life={"REGISTERED","IN_STOCK","DEPLOYED","DEREGISTERED"},condition={"UNKNOWN","WORKING","DEFECTIVE","UNUSABLE"};String[] graph=kind.equals("lifecycle")?life:condition;
  jdbc.update("UPDATE asset SET "+kind+"=?,loaned_to=?,loaned_at=? WHERE id=?",graph[from],loan?"Borrower":null,loan?java.sql.Timestamp.from(Instant.now()):null,a.id());
  boolean allowed=kind.equals("lifecycle")?from!=3 && from!=to:from!=3 && from!=to && !(from==1 && to==0) && !(from==2 && to==0);
  var body=new LinkedHashMap<String,Object>();body.put("expectedVersion",0);body.put("target",graph[to]);if(kind.equals("lifecycle") && to==3)body.put("reason","LOST");
  var result=request(admin,post("/api/v1/assets/"+a.id()+"/"+kind+"-transitions"),body);assertEquals(allowed?200:409,status(result),result.getResponse().getContentAsString());
  assertEquals(allowed?2:1,events(a.id()));var current=assets.get(tenant,a.id());assertEquals(loan?"Borrower":null,current.loanedTo());assertEquals(location,current.locationId());assertEquals(owner,current.ownerId());assertEquals(product,current.productId());
  if(allowed){String expected=kind.equals("condition")?"CONDITION_CHANGED":to==3?"DEREGISTERED":"LIFECYCLE_CHANGED";assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM asset_event WHERE asset_id=? AND type=?",Integer.class,a.id(),expected));assertEquals(1,current.version());if(to==3 && kind.equals("lifecycle"))assertEquals("LOST",jdbc.queryForObject("SELECT data->>'reason' FROM asset_event WHERE asset_id=? AND type='DEREGISTERED'",String.class,a.id()));}else assertEquals("INVALID_TRANSITION",data(result).get("code"));
 }
 @Test void registrationTrustBoundaryAndReferenceSelection()throws Exception{
  var b=new LinkedHashMap<String,Object>();b.put("productId",product);b.put("locationId",location);b.put("loanable",true);b.put("serialNumber","  ");var r=request(admin,post("/api/v1/tenants/"+tenant+"/assets"),b);assertEquals(201,status(r));assertEquals(category.toString(),data(r).get("categoryId"));assertNull(data(r).get("serialNumber"));assertEquals("REGISTERED",data(r).get("lifecycle"));assertEquals("UNKNOWN",data(r).get("condition"));assertNull(data(r).get("loanedTo"));
  for(String field:List.of("categoryId","manufacturerId","tenantId","actorId","assetTag","lifecycle","version","loanedTo")){var bad=new LinkedHashMap<>(b);bad.put(field,field.endsWith("Id")?category:"forged");assertEquals(400,status(request(admin,post("/api/v1/tenants/"+tenant+"/assets"),bad)),field);}
  var direct=Map.of("categoryId",category,"manufacturerId",manufacturer,"locationId",location,"loanable",false);assertEquals(201,status(request(admin,post("/api/v1/tenants/"+tenant+"/assets"),direct)));
  var missing=new LinkedHashMap<>(direct);missing.remove("manufacturerId");assertEquals(400,status(request(admin,post("/api/v1/tenants/"+tenant+"/assets"),missing)));
  UUID foreign=places.create(PlaceService.Kind.LOCATION,other,"Foreign",actor);b.put("locationId",foreign);assertEquals(404,status(request(admin,post("/api/v1/tenants/"+tenant+"/assets"),b)));b.put("locationId",location);b.put("ownerId",places.create(PlaceService.Kind.OWNER,other,"Foreign",actor));assertEquals(404,status(request(admin,post("/api/v1/tenants/"+tenant+"/assets"),b)));
  UUID deleted=catalog.createProduct(null,category,manufacturer,"Deleted",null,actor);catalog.delete(CatalogService.Kind.PRODUCT,deleted,actor);b.remove("ownerId");b.put("productId",deleted);var denied=request(admin,post("/api/v1/tenants/"+tenant+"/assets"),b);assertEquals(409,status(denied));assertEquals("DELETED_TARGET",data(denied).get("code"));
 }
 @Test void exactLoanAndReturnGuardsWithTerminalStates()throws Exception{
  var a=asset();var noLoan=request(admin,post("/api/v1/assets/"+a.id()+"/return"),Map.of("expectedVersion",0));assertEquals("NO_CURRENT_LOAN",data(noLoan).get("code"));
  for(String update:List.of("loanable=false,lifecycle='DEPLOYED',condition='WORKING'","loanable=true,lifecycle='IN_STOCK',condition='WORKING'","loanable=true,lifecycle='DEPLOYED',condition='DEFECTIVE'")){jdbc.update("UPDATE asset SET "+update+" WHERE id=?",a.id());var r=request(admin,post("/api/v1/assets/"+a.id()+"/loan"),Map.of("expectedVersion",0,"borrower","Borrower"));assertEquals("LOAN_GUARD",data(r).get("code"));}
  jdbc.update("UPDATE asset SET loanable=true,lifecycle='DEPLOYED',condition='WORKING' WHERE id=?",a.id());var r=request(admin,post("/api/v1/assets/"+a.id()+"/loan"),Map.of("expectedVersion",0,"borrower"," Borrower ","expectedReturnAt","2020-01-01T00:00:00Z","loanNote"," Note "));assertEquals(200,status(r));assertEquals("Borrower",data(r).get("loanedTo"));
  assertEquals("LOAN_GUARD",data(request(admin,post("/api/v1/assets/"+a.id()+"/loan"),Map.of("expectedVersion",1,"borrower","Other"))).get("code"));assertEquals("OUTSTANDING_LOAN",data(request(admin,put("/api/v1/assets/"+a.id()+"/loanability"),Map.of("expectedVersion",1,"loanable",false))).get("code"));
  assertEquals(200,status(request(admin,post("/api/v1/assets/"+a.id()+"/lifecycle-transitions"),Map.of("expectedVersion",1,"target","DEREGISTERED","reason","LOST"))));assertEquals(200,status(request(admin,post("/api/v1/assets/"+a.id()+"/condition-transitions"),Map.of("expectedVersion",2,"target","UNUSABLE"))));
  // Existing persisted loan is returnable even if issuance eligibility is independently absent.
  jdbc.update("UPDATE asset SET loanable=false WHERE id=?",a.id());var returned=request(admin,post("/api/v1/assets/"+a.id()+"/return"),Map.of("expectedVersion",3));assertEquals(200,status(returned));for(String key:List.of("loanedTo","loanedAt","expectedReturnAt","loanNote"))assertNull(data(returned).get(key));assertEquals(location.toString(),data(returned).get("locationId"));assertEquals("DEREGISTERED",data(returned).get("lifecycle"));assertEquals("UNUSABLE",data(returned).get("condition"));assertEquals(5,events(a.id()));
 }
 @Test void separateEditsHistoryAndStaleVersions()throws Exception{
  var a=asset();assertEquals(400,status(request(admin,put("/api/v1/assets/"+a.id()+"/metadata"),Map.of("expectedVersion",0,"loanable",false))));assertEquals(200,status(request(admin,put("/api/v1/assets/"+a.id()+"/metadata"),Map.of("expectedVersion",0,"serialNumber","SN","note","<script>safe text</script>"))));var stale=request(admin,put("/api/v1/assets/"+a.id()+"/loanability"),Map.of("expectedVersion",0,"loanable",false));assertEquals(409,status(stale));assertEquals("STALE_ASSET_VERSION",data(stale).get("code"));
  assertEquals(200,status(request(admin,put("/api/v1/assets/"+a.id()+"/loanability"),Map.of("expectedVersion",1,"loanable",false))));assertEquals(200,status(request(admin,put("/api/v1/assets/"+a.id()+"/catalog"),Map.of("expectedVersion",2,"categoryId",category,"manufacturerId",manufacturer))));
  var frozen=request(admin,put("/api/v1/catalog/products/"+product),Map.of("name","Edited","categoryId",category,"manufacturerId",manufacturer));assertEquals("PRODUCT_EDIT_FROZEN",data(frozen).get("code"));var remove=new LinkedHashMap<String,Object>();remove.put("ownerId",null);remove.put("expectedVersion",3);assertEquals(200,status(request(admin,put("/api/v1/assets/"+a.id()+"/owner"),remove)));
  UUID next=places.create(PlaceService.Kind.LOCATION,tenant,"Next",actor);assertEquals(200,status(request(admin,put("/api/v1/assets/"+a.id()+"/location"),Map.of("expectedVersion",4,"locationId",next))));places.edit(PlaceService.Kind.LOCATION,tenant,next,"Renamed",actor);assertEquals(6,events(a.id()));assertEquals(5,assets.get(tenant,a.id()).version());var history=request(admin,get("/api/v1/assets/"+a.id()+"/events?size=2"),null);assertEquals(6L,((Number)data(history).get("totalElements")).longValue());assertTrue(history.getResponse().getContentAsString().contains("Next"));assertFalse(history.getResponse().getContentAsString().contains("Renamed"));
  for(String route:List.of("/assets/"+a.id(),"/assets/"+a.id()+"/restore","/catalog/products/merge","/owners/merge","/locations/merge")){assertEquals(404,status(request(admin,route.contains("/assets/")&&!route.endsWith("restore")?delete("/api/v1"+route):post("/api/v1"+route),Map.of())));}
 }
 @Test void literalSearchPaginationAndRetainedVisibility()throws Exception{
  var a=asset();assets.editText(tenant,a.id(),0,"Literal%_\\Text","External","Note",actor);var hidden=asset();assets.delete(tenant,hidden.id(),0,actor);var dead=asset();assets.lifecycle(tenant,dead.id(),0,LifecycleState.DEREGISTERED,DeregistrationReason.ERROR,null,actor);
  var r=request(admin,get("/api/v1/tenants/"+tenant+"/assets").param("q","%_\\"),null);assertEquals(200,status(r));assertEquals(1L,((Number)data(r).get("totalElements")).longValue());assertEquals(404,status(request(admin,get("/api/v1/assets/"+hidden.id()),null)));
  var list=request(admin,get("/api/v1/tenants/"+tenant+"/assets?size=1&sort=tag&direction=asc"),null);assertEquals(2L,((Number)data(list).get("totalElements")).longValue());assertEquals(1,((List<?>)data(list).get("items")).size());assertEquals(400,status(request(admin,get("/api/v1/assets?size=101"),null)));assertEquals(400,status(request(admin,get("/api/v1/assets?deleted=true"),null)));assertEquals(400,status(request(admin,get("/api/v1/assets").param("q","x".repeat(129)),null)));
  UUID foreign=places.create(PlaceService.Kind.OWNER,other,"Other",actor);assertEquals(404,status(request(principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE),get("/api/v1/tenants/"+tenant+"/assets?ownerId="+foreign),null)));
 }
 @ParameterizedTest @CsvSource({"GLOBAL,ADMIN","GLOBAL,USER","TENANT,ADMIN","TENANT,USER"}) void roleMatrixAndTenantIsolation(Scope scope,Role role)throws Exception{
  var who=principal(scope,role,Status.ACTIVE);var a=asset();boolean writer=role==Role.ADMIN;assertEquals(200,status(request(who,get("/api/v1/tenants/"+tenant+"/assets"),null)));assertEquals(scope==Scope.GLOBAL?200:403,status(request(who,get("/api/v1/assets"),null)));
  assertEquals(writer?200:403,status(request(who,put("/api/v1/assets/"+a.id()+"/metadata"),Map.of("expectedVersion",0,"note","Role test"))));assertEquals(writer?200:403,status(request(who,get("/api/v1/audit-log"),null)));var foreignPath=request(who,get("/api/v1/tenants/"+other+"/assets"),null);assertEquals(200,status(foreignPath));if(scope==Scope.TENANT){assertEquals(1L,((Number)data(foreignPath).get("totalElements")).longValue());assertEquals(a.id().toString(),((Map<?,?>)((List<?>)data(foreignPath).get("items")).getFirst()).get("id"));}
  UUID otherLoc=places.create(PlaceService.Kind.LOCATION,other,"Foreign",actor);assertEquals(scope==Scope.GLOBAL?200:404,status(request(who,get("/api/v1/locations/"+otherLoc),null)));assertEquals(scope==Scope.GLOBAL && writer?204:403,status(request(who,post("/api/v1/catalog/categories/"+catalog.createCategory(tenant,"Publish",actor)+"/publish"),Map.of())));
  for(String route:List.of("/assets/"+a.id(),"/assets/"+a.id()+"/events","/tenants/"+tenant+"/assets","/catalog/categories","/tenants/"+tenant+"/locations","/audit-log"))assertEquals(403,status(request(principal(scope,role,Status.PENDING),get("/api/v1"+route),null)));
  assertEquals(401,status(request(null,get("/api/v1/assets"),null)));assertEquals(401,status(mvc.perform(get("/api/v1/assets").header("Authorization","Bearer forged")).andReturn()));
 }
 @Test void auditQueryAttributionNotActorAndNoExistenceOrCountLeak()throws Exception{
  UUID local=catalog.createCategory(tenant,"Tenant owned",actor);UUID foreign=catalog.createCategory(other,"Other owned",actor);catalog.publish(CatalogService.Kind.CATEGORY,local,actor);catalog.editLabel(CatalogService.Kind.CATEGORY,local,"Global now",actor);
  assertEquals(tenant,attribution("TENANT_CREATED",tenant));assertEquals(tenant,attribution("CATEGORY_CREATED",local));assertEquals(tenant,attribution("CATEGORY_PUBLISHED",local));assertNull(attribution("CATEGORY_EDITED",local));assertEquals(tenant,attribution("LOCATION_CREATED",location));assertNull(attribution("PRODUCT_CREATED",product));
  var who=principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE);long expected=jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE tenant_id=?",Long.class,tenant);var r=request(who,get("/api/v1/audit-log?size=1"),null);assertEquals(expected,((Number)data(r).get("totalElements")).longValue());for(var entry:(List<?>)data(r).get("items"))assertEquals(tenant.toString(),((Map<?,?>)entry).get("tenantId"));
  UUID foreignAudit=jdbc.queryForObject("SELECT id FROM audit_log WHERE target_id=?",UUID.class,foreign);UUID globalAudit=jdbc.queryForObject("SELECT id FROM audit_log WHERE target_id=?",UUID.class,product);for(UUID id:List.of(foreignAudit,globalAudit,UUID.randomUUID())){var missing=request(who,get("/api/v1/audit-log/"+id),null);assertEquals(404,status(missing));assertEquals("NOT_FOUND",data(missing).get("code"));}
  assertEquals(403,status(request(who,get("/api/v1/audit-log?tenantId="+other),null)));assertEquals(403,status(request(who,get("/api/v1/audit-log?globalOnly=true"),null)));assertEquals(0L,((Number)data(request(who,get("/api/v1/audit-log?targetId="+foreign),null)).get("totalElements")).longValue());assertEquals(200,status(request(admin,get("/api/v1/audit-log/"+foreignAudit),null)));
 }
 @ParameterizedTest @CsvSource({"CATEGORY,TT","CATEGORY,TG","CATEGORY,GG","CATEGORY,GT","CATEGORY,TO","MANUFACTURER,TT","MANUFACTURER,TG","MANUFACTURER,GG","MANUFACTURER,GT","MANUFACTURER,TO"}) void allLegalMergeOwnershipAndAdvisoryPreview(CatalogService.MergeKind kind,String combination)throws Exception{
  UUID sourceTenant=combination.startsWith("T")?tenant:null,targetTenant=combination.endsWith("G")?null:combination.equals("TO")?other:tenant;
  java.util.function.BiFunction<UUID,String,UUID> create=(t,n)->kind==CatalogService.MergeKind.CATEGORY?catalog.createCategory(t,n,actor):catalog.createManufacturer(t,n,actor);
  UUID source=create.apply(sourceTenant,"Source"),target=create.apply(targetTenant,"Target");String plural=kind==CatalogService.MergeKind.CATEGORY?"categories":"manufacturers";var preview=request(admin,post("/api/v1/catalog/"+plural+"/merge-preview"),Map.of("sourceId",source,"targetId",target));assertEquals(200,status(preview));assertEquals(0L,((Number)data(preview).get("assetCount")).longValue());
  AssetService.View a=null;if(!combination.equals("TO")){a=assets.register(tenant,new AssetService.Registration(new AssetService.DirectSelection(kind==CatalogService.MergeKind.CATEGORY?source:category,kind==CatalogService.MergeKind.MANUFACTURER?source:manufacturer),location,null,false,null,null,null),actor);}
  assertEquals(204,status(request(admin,post("/api/v1/catalog/"+plural+"/merge"),Map.of("sourceId",source,"targetId",target))));assertEquals(sourceTenant,attribution(kind+"_MERGED",source));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action=? AND target_id=?",Integer.class,kind+"_MERGED",source));if(a!=null){assertEquals(1,assets.get(tenant,a.id()).version());assertEquals(2,events(a.id()));}
 }
 @Test void allAdministrativeWriterVariantsAndSecretsExcluded(){
  Identity real=auth.bootstrap("admin-"+UUID.randomUUID().toString().substring(0,8)+"@example.org","Administrator","CorrectHorse25!Battery");assertNull(attribution("GLOBAL_ADMIN_BOOTSTRAPPED",real.id()));Identity global=auth.createGlobal(real,"global-"+UUID.randomUUID().toString().substring(0,8)+"@example.org","Global","CorrectHorse25!Battery");assertNull(attribution("USER_CREATED",global.id()));auth.resetCredential(real,global.id(),"reset-"+UUID.randomUUID().toString().substring(0,8)+"@example.org","CorrectHorse25!Battery");assertNull(attribution("LOCAL_CREDENTIAL_CHANGED",global.id()));auth.ownPassword(global,"CorrectHorse25!Battery","ChangedHorse25!Battery");assertNull(attribution("LOCAL_CREDENTIAL_CHANGED",global.id()));
  UUID group=UUID.randomUUID();var cfg=configs.put(real,tenant,"https://iserv.example","client","secret-value",true,null,Set.of(group));assertEquals(tenant,attribution("OIDC_CONFIG_CHANGED",tenant));Identity jit=provisioning.complete(tenant,cfg.version(),"https://iserv.example","client","subject","Tenant user",Set.of(group)).user();assertEquals(Status.PENDING,jit.status());assertEquals(tenant,attribution("USER_CREATED",jit.id()));auth.approve(real,jit.id());auth.role(real,jit.id(),Role.ADMIN);auth.disable(real,jit.id());for(String action:List.of("USER_APPROVED","USER_ROLE_CHANGED","USER_DISABLED"))assertEquals(tenant,attribution(action,jit.id()));auth.role(real,global.id(),Role.ADMIN);auth.disable(real,global.id());assertNull(attribution("USER_ROLE_CHANGED",global.id()));assertNull(attribution("USER_DISABLED",global.id()));
  for(boolean isGlobal:List.of(false,true))for(CatalogService.Kind kind:CatalogService.Kind.values()){
   UUID t=isGlobal?null:tenant;UUID id=switch(kind){case CATEGORY->catalog.createCategory(t,"Create",actor);case MANUFACTURER->catalog.createManufacturer(t,"Create",actor);case PRODUCT->catalog.createProduct(t,category,manufacturer,"Create",null,actor);};assertEquals(t,attribution(kind+"_CREATED",id));if(kind==CatalogService.Kind.PRODUCT)catalog.editProduct(id,category,manufacturer,"Edited",null,actor);else catalog.editLabel(kind,id,"Edited",actor);assertEquals(t,attribution(kind+"_EDITED",id));if(!isGlobal){catalog.publish(kind,id,actor);assertEquals(tenant,attribution(kind+"_PUBLISHED",id));if(kind==CatalogService.Kind.PRODUCT)catalog.editProduct(id,category,manufacturer,"Global edit",null,actor);else catalog.editLabel(kind,id,"Global edit",actor);assertNull(attribution(kind+"_EDITED",id));}catalog.delete(kind,id,actor);assertNull(attribution(kind+"_DELETED",id));
   if(!isGlobal){UUID deleted=switch(kind){case CATEGORY->catalog.createCategory(tenant,"Delete",actor);case MANUFACTURER->catalog.createManufacturer(tenant,"Delete",actor);case PRODUCT->catalog.createProduct(tenant,category,manufacturer,"Delete",null,actor);};catalog.delete(kind,deleted,actor);assertEquals(tenant,attribution(kind+"_DELETED",deleted));}
  }
  for(PlaceService.Kind kind:PlaceService.Kind.values()){UUID id=places.create(kind,tenant,"Create",actor);places.edit(kind,tenant,id,"Edited",actor);places.delete(kind,tenant,id,actor);for(String verb:List.of("CREATED","EDITED","DELETED"))assertEquals(tenant,attribution(kind+"_"+verb,id));}
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE data::text LIKE '%secret-value%' OR data::text LIKE '%CorrectHorse%' OR data::text LIKE '%ChangedHorse%'",Integer.class));
  // Recovery uses the same attribution, not a lifetime bootstrap flag.
  jdbc.update("UPDATE app_user SET status='DISABLED' WHERE scope='GLOBAL' AND role='ADMIN'");var recovered=auth.bootstrap("recovery-"+UUID.randomUUID().toString().substring(0,8)+"@example.org","Recovery","CorrectHorse25!Battery");assertNull(attribution("GLOBAL_ADMIN_BOOTSTRAPPED",recovered.id()));
 }
 @Test void eventFailureRollsBackWorkflow(){var a=asset();jdbc.execute("CREATE FUNCTION phase3_fail_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture'; END $$");jdbc.execute("CREATE TRIGGER phase3_fail BEFORE INSERT ON asset_event FOR EACH ROW EXECUTE FUNCTION phase3_fail_event()");try{assertThrows(RuntimeException.class,()->assets.lifecycle(tenant,a.id(),0,LifecycleState.DEREGISTERED,DeregistrationReason.LOST,null,actor));}finally{jdbc.execute("DROP TRIGGER phase3_fail ON asset_event");jdbc.execute("DROP FUNCTION phase3_fail_event()");}assertEquals(LifecycleState.REGISTERED,assets.get(tenant,a.id()).lifecycle());assertEquals(0,assets.get(tenant,a.id()).version());assertEquals(1,events(a.id()));}
 <T>T tx(java.util.function.Supplier<T> work){return new TransactionTemplate(tm).execute(s->work.get());}
 void await(CountDownLatch latch){try{assertTrue(latch.await(15,TimeUnit.SECONDS));}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new AssertionError(ex);}}
 void blocked(String name)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<until){if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')",Boolean.class,name))return;Thread.sleep(20);}fail("No actual PostgreSQL lock wait observed");}
 @ParameterizedTest @ValueSource(strings={"loan","return","lifecycle","merge"}) void independentConnectionsCompeteWithoutLostUpdate(String operation)throws Exception{
  var a=asset();jdbc.update("UPDATE asset SET lifecycle='DEPLOYED',condition='WORKING' WHERE id=?",a.id());if(operation.equals("return"))a=assets.loan(tenant,a.id(),0,"Borrower",null,null,actor);final var start=a;var held=new CountDownLatch(1);var release=new CountDownLatch(1);String name="phase3_"+UUID.randomUUID();UUID target=catalog.createCategory(null,"Merge target",actor);
  try(var pool=Executors.newFixedThreadPool(2)){
   Future<?> first=pool.submit(()->tx(()->{if(operation.equals("merge"))merges.merge(CatalogService.MergeKind.CATEGORY,category,target,actor);else if(operation.equals("loan"))assets.loan(tenant,start.id(),start.version(),"Winner",null,null,actor);else if(operation.equals("return"))assets.returned(tenant,start.id(),start.version(),actor);else assets.lifecycle(tenant,start.id(),start.version(),LifecycleState.IN_STOCK,null,null,actor);held.countDown();await(release);return null;}));await(held);
   Future<?> second=pool.submit(()->tx(()->{jdbc.queryForObject("SELECT set_config('application_name',?,true)",String.class,name);return operation.equals("loan")?assets.loan(tenant,start.id(),start.version(),"Loser",null,null,actor):operation.equals("return")?assets.returned(tenant,start.id(),start.version(),actor):assets.condition(tenant,start.id(),start.version(),ConditionState.DEFECTIVE,null,actor);}));try{blocked(name);}finally{release.countDown();}first.get(15,TimeUnit.SECONDS);var failed=assertThrows(ExecutionException.class,()->second.get(15,TimeUnit.SECONDS));assertInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class,failed.getCause());
  }assertEquals(start.version()+1,assets.get(tenant,start.id()).version());assertEquals(start.version()+2,events(start.id()));assertEquals(ConditionState.WORKING,assets.get(tenant,start.id()).condition());
 }
 @Test void freshAndPopulatedV4MigrationPreservesEverythingExceptDisposableAudit(){
  var a=asset();UUID global=users.create(Scope.GLOBAL,null,Role.USER,Status.ACTIVE,"Credential owner");UUID local=users.create(Scope.TENANT,tenant,Role.USER,Status.PENDING,"External owner");
  jdbc.update("INSERT INTO local_global_credential(user_id,email,password_hash,email_verified_at) VALUES(?,?,'fixture-hash',now())",global,"migration-"+global.toString().substring(0,8)+"@example.org");
  jdbc.update("INSERT INTO tenant_oidc_config(tenant_id,issuer,client_id,encrypted_client_secret,enabled,version,created_by,created_at,updated_by,updated_at) VALUES(?,'https://fixture.example','client','encrypted-fixture',true,0,?,now(),?,now())",tenant,actor.id(),actor.id());jdbc.update("INSERT INTO tenant_oidc_allowed_group VALUES(?,?)",tenant,UUID.randomUUID());jdbc.update("INSERT INTO external_identity(issuer,subject,user_id,tenant_id) VALUES('https://fixture.example',?,?,?)",local.toString(),local,tenant);
  String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");var flyway=org.flywaydb.core.Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas(schema).defaultSchema(schema).target("4").load();flyway.migrate();
  var tables=List.of("tenant","app_user","category","manufacturer","product","location","owner","asset","asset_event","asset_tag_counter","local_global_credential","tenant_oidc_config","tenant_oidc_allowed_group","external_identity","global_admin_recovery_guard");
  var before=new LinkedHashMap<String,List<String>>();
  for(String table:tables){String columns=String.join(",",jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name=? ORDER BY ordinal_position",String.class,schema,table));jdbc.execute("INSERT INTO "+schema+"."+table+" SELECT "+(table.equals("local_global_credential")?columns.replace("login_name","split_part(email,'@',1)"):columns)+" FROM public."+table+" ON CONFLICT DO NOTHING");before.put(table,jdbc.queryForList("SELECT row_to_json(t)::text FROM "+schema+"."+table+" t ORDER BY row_to_json(t)::text",String.class));assertFalse(before.get(table).isEmpty(),table);}
  jdbc.execute("INSERT INTO "+schema+".audit_log(id,action,target_type,target_id,occurred_at,actor_id,actor_display_name,data) SELECT id,action,target_type,target_id,occurred_at,actor_id,actor_display_name,data FROM public.audit_log");assertTrue(jdbc.queryForObject("SELECT count(*) FROM "+schema+".audit_log",Long.class)>0);
  var checksums=jdbc.queryForList("SELECT checksum FROM "+schema+".flyway_schema_history WHERE version IN ('1','2','3','4') ORDER BY installed_rank",Integer.class);
  org.flywaydb.core.Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas(schema).defaultSchema(schema).target("5").load().migrate();
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM "+schema+".audit_log",Integer.class));assertEquals(checksums,jdbc.queryForList("SELECT checksum FROM "+schema+".flyway_schema_history WHERE version IN ('1','2','3','4') ORDER BY installed_rank",Integer.class));for(String table:tables)assertEquals(before.get(table),jdbc.queryForList("SELECT row_to_json(t)::text FROM "+schema+"."+table+" t ORDER BY row_to_json(t)::text",String.class),table);
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conrelid=?::regclass AND contype='f'",Integer.class,schema+".audit_log"));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND table_name='audit_log' AND column_name='tenant_id' AND is_nullable='YES'",Integer.class,schema));
  jdbc.update("INSERT INTO "+schema+".audit_log(id,tenant_id,action,target_type,target_id,occurred_at,actor_id,actor_display_name,data) VALUES(?,?,'TENANT_CREATED','TENANT',?,now(),?,'Snapshot','{}')",UUID.randomUUID(),tenant,tenant,actor.id());assertEquals(tenant,jdbc.queryForObject("SELECT tenant_id FROM "+schema+".audit_log",UUID.class));assertEquals(8,jdbc.queryForObject("SELECT count(*) FROM public.flyway_schema_history WHERE success",Integer.class));
 }

 @ParameterizedTest @CsvSource({"GLOBAL,ADMIN","GLOBAL,USER","TENANT,ADMIN","TENANT,USER"}) void roleMatrixForEveryMutationFamily(Scope scope,Role role)throws Exception{
  Identity who=principal(scope,role,Status.ACTIVE);boolean write=role==Role.ADMIN;
  UUID next=places.create(PlaceService.Kind.LOCATION,tenant,"Next",actor);
  for(String command:List.of("metadata","catalog","loanability","lifecycle-transitions","condition-transitions","location","owner","loan","return")){
   var a=asset();var b=new LinkedHashMap<String,Object>();b.put("expectedVersion",0);
   switch(command){case "metadata"->b.put("note","Text");case "catalog"->b.put("productId",product);case "loanability"->b.put("loanable",false);case "lifecycle-transitions"->b.put("target","DEPLOYED");case "condition-transitions"->b.put("target","WORKING");case "location"->b.put("locationId",next);case "owner"->b.put("ownerId",null);case "loan"->{jdbc.update("UPDATE asset SET lifecycle='DEPLOYED',condition='WORKING' WHERE id=?",a.id());b.put("borrower","Borrower");}case "return"->jdbc.update("UPDATE asset SET loaned_to='Existing',loaned_at=now() WHERE id=?",a.id());}
   boolean put=Set.of("metadata","catalog","loanability","location","owner").contains(command);var r=request(who,put?put("/api/v1/assets/"+a.id()+"/"+command):post("/api/v1/assets/"+a.id()+"/"+command),b);assertEquals(write?200:403,status(r),command+": "+r.getResponse().getContentAsString());assertEquals(write?2:1,events(a.id()));
  }
  for(String kind:List.of("categories","manufacturers","products","locations","owners")){
   boolean c=List.of("categories","manufacturers","products").contains(kind);String path="/api/v1/tenants/"+tenant+"/"+(c?"catalog/":"")+kind;var b=new LinkedHashMap<String,Object>();b.put("name","Role created");if(kind.equals("products")){b.put("categoryId",category);b.put("manufacturerId",manufacturer);}
   var created=request(who,post(path),b);assertEquals(write?201:403,status(created),kind);assertEquals(200,status(request(who,get(path),null)));if(write){UUID id=UUID.fromString((String)data(created).get("id"));String item="/api/v1/"+(c?"catalog/":"")+kind+"/"+id;b.put("name","Role edited");assertEquals(204,status(request(who,put(item),b)));assertEquals(204,status(request(who,delete(item),null)));}
  }
  var merge=request(who,post("/api/v1/catalog/categories/merge-preview"),Map.of("sourceId",category,"targetId",catalog.createCategory(null,"Target",actor)));assertEquals(scope==Scope.GLOBAL&&write?200:403,status(merge));
 }
 @Test void allStructuredDeregistrationReasonsAndInvalidInputs()throws Exception{
  for(String reason:List.of("TRANSFER","DISPOSAL","LOST","ERROR")){var a=asset();var r=request(admin,post("/api/v1/assets/"+a.id()+"/lifecycle-transitions"),Map.of("target","DEREGISTERED","reason",reason,"expectedVersion",0));assertEquals(200,status(r));assertEquals(reason,jdbc.queryForObject("SELECT data->>'reason' FROM asset_event WHERE asset_id=? AND type='DEREGISTERED'",String.class,a.id()));}
  var a=asset();for(Map<String,Object> b:List.of(Map.<String,Object>of("target","DEREGISTERED","expectedVersion",0),Map.<String,Object>of("target","DEPLOYED","reason","LOST","expectedVersion",0),Map.<String,Object>of("target","DEREGISTERED","reason","OTHER","expectedVersion",0),Map.<String,Object>of("target","DEPLOYED","expectedVersion",-1))){assertEquals(400,status(request(admin,post("/api/v1/assets/"+a.id()+"/lifecycle-transitions"),b)));}assertEquals(1,events(a.id()));assertEquals(400,status(request(admin,get("/api/v1/assets/not-a-uuid"),null)));
 }
 @Test void catalogAndPlaceHttpGuardsKeepRetainedReferences()throws Exception{
  var a=asset();assets.lifecycle(tenant,a.id(),0,LifecycleState.DEREGISTERED,DeregistrationReason.LOST,null,actor);
  for(String path:List.of("catalog/categories/"+category,"catalog/manufacturers/"+manufacturer,"catalog/products/"+product,"locations/"+location,"owners/"+owner)){assertEquals("REFERENCED",data(request(admin,delete("/api/v1/"+path),null)).get("code"));}
  assertEquals("PROTECTED_UNKNOWN",data(request(admin,put("/api/v1/catalog/categories/"+CatalogService.UNKNOWN_CATEGORY),Map.of("name","Altered"))).get("code"));
  assertEquals("PROTECTED_UNKNOWN",data(request(admin,post("/api/v1/catalog/manufacturers/merge"),Map.of("sourceId",CatalogService.UNKNOWN_MANUFACTURER,"targetId",manufacturer))).get("code"));
  assertEquals(204,status(request(admin,put("/api/v1/locations/"+location),Map.of("name","Renamed"))));assertEquals(2,events(a.id()));assertEquals(1,assets.get(tenant,a.id()).version());
  UUID c=catalog.createCategory(tenant,"Dependency",actor),p=catalog.createProduct(tenant,c,manufacturer,"Tenant product",null,actor);assertEquals("INVALID_DEPENDENCY",data(request(admin,post("/api/v1/catalog/products/"+p+"/publish"),Map.of())).get("code"));
  UUID deleted=places.create(PlaceService.Kind.LOCATION,tenant,"Deleted",actor);places.delete(PlaceService.Kind.LOCATION,tenant,deleted,actor);assertEquals("DELETED_TARGET",data(request(admin,put("/api/v1/assets/"+a.id()+"/location"),Map.of("locationId",deleted,"expectedVersion",1))).get("code"));
 }
 @Test void lockTimeoutHttpIsSafeConflictAndRollsBack()throws Exception{
  var a=asset();var held=new CountDownLatch(1);var release=new CountDownLatch(1);
  try(var pool=Executors.newSingleThreadExecutor()){
   var holder=pool.submit(()->tx(()->{jdbc.queryForObject("SELECT id FROM category WHERE id=? FOR UPDATE",UUID.class,category);held.countDown();await(release);return null;}));await(held);
   final java.util.concurrent.atomic.AtomicReference<MvcResult> response=new java.util.concurrent.atomic.AtomicReference<>();
   try{assertThrows(org.springframework.transaction.UnexpectedRollbackException.class,()->tx(()->{jdbc.execute("SET LOCAL lock_timeout='100ms'");try{response.set(request(admin,put("/api/v1/assets/"+a.id()+"/metadata"),Map.of("expectedVersion",0,"note","Must rollback")));}catch(Exception ex){throw new RuntimeException(ex);}return null;}));assertEquals(409,status(response.get()));assertEquals("CONCURRENT_OPERATION",data(response.get()).get("code"));}finally{release.countDown();}holder.get(15,TimeUnit.SECONDS);
  }assertEquals(0,assets.get(tenant,a.id()).version());assertNull(assets.get(tenant,a.id()).note());assertEquals(1,events(a.id()));
 }

 @AfterAll static void completeAuditActionCoverage()throws Exception{
  var db=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()));
  var expected=new TreeSet<String>(List.of("TENANT_CREATED","OIDC_CONFIG_CHANGED","USER_CREATED","USER_APPROVED","USER_ROLE_CHANGED","USER_DISABLED","LOCAL_CREDENTIAL_CHANGED","GLOBAL_ADMIN_BOOTSTRAPPED","CATEGORY_MERGED","MANUFACTURER_MERGED"));
  for(String entity:List.of("CATEGORY","MANUFACTURER","PRODUCT"))for(String action:List.of("CREATED","EDITED","DELETED","PUBLISHED"))expected.add(entity+"_"+action);
  for(String entity:List.of("LOCATION","OWNER"))for(String action:List.of("CREATED","EDITED","DELETED"))expected.add(entity+"_"+action);
  var actions=new TreeSet<>(db.queryForList("SELECT DISTINCT action FROM audit_log",String.class));assertEquals(expected,actions);assertEquals(28,actions.size());
  java.nio.file.Files.writeString(java.nio.file.Path.of("target/phase3-audit-actions.json"),JsonMapper.builder().build().writeValueAsString(db.queryForList("SELECT action,count(*) AS records FROM audit_log GROUP BY action ORDER BY action")));
 }

}
