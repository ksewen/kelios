package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.audit.*;
import com.github.ksewen.kelios.catalog.*;
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
class UserAdministrationIT {
 static class MutableClock extends Clock {
  Instant now=Instant.parse("2026-10-11T12:00:00Z");
  public Instant instant(){return now;} public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
 }
 @org.springframework.boot.test.context.TestConfiguration static class Time {
  @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary MutableClock labelClock(){return new MutableClock();}
 }
 @Autowired MutableClock clock;
 @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17-alpine");
 static final AuthTestKeys KEYS;static{try{KEYS=new AuthTestKeys("user-admin-keys");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",PG::getJdbcUrl);p.add("spring.datasource.username",PG::getUsername);p.add("spring.datasource.password",PG::getPassword);p.add("kelios.auth.key-directory",()->KEYS.directory.toAbsolutePath().toString());p.add("kelios.auth.public-origin",()->"https://kelios.example");p.add("kelios.auth.issuer",()->"https://kelios.example");}
 @Autowired AssetService assets;@Autowired CatalogService catalog;@Autowired PlaceService places;@Autowired CatalogMergeService merges;
 @Autowired TenantService tenants;@Autowired UserService users;@Autowired IdentityStore identities;@Autowired AuthenticationService auth;
 @Autowired OidcConfigurationService configs;@Autowired OidcProvisioning provisioning;@Autowired JwtTokens tokens;
 @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager tm;@Autowired WebApplicationContext context;
 final JsonMapper json=JsonMapper.builder().build();final Actor actor=new Actor(UUID.randomUUID(),"Global historical actor");
 MockMvc mvc;UUID tenant,other,category,manufacturer,product,location,owner;Identity admin;
 @BeforeEach void setup(){clock.now=Instant.parse("2026-10-11T12:00:00Z");tenant=tenants.create("Tenant "+UUID.randomUUID(),actor);other=tenants.create("Other",actor);admin=new Identity(actor.id(),Scope.GLOBAL,null,Role.ADMIN,Status.ACTIVE,actor.displayName());mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 AssetService.View asset(){return assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(product),location,owner,true,null,null,null),actor);}
 MvcResult request(Identity who,MockHttpServletRequestBuilder b,Object body)throws Exception{if(who!=null)b.header("Authorization","Bearer "+tokens.login(who).accessToken());if(body!=null)b.contentType("application/json").content(json.writeValueAsString(body));return mvc.perform(b).andReturn();}
 Map<String,Object> data(MvcResult r){return json.readValue(r.getResponse().getContentAsByteArray(),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});}
 int status(MvcResult r){return r.getResponse().getStatus();}
 long events(UUID id){return jdbc.queryForObject("SELECT count(*) FROM asset_event WHERE asset_id=?",Long.class,id);}
 UUID attribution(String action,UUID id){return jdbc.queryForObject("SELECT tenant_id FROM audit_log WHERE action=? AND target_id=? ORDER BY occurred_at DESC,id DESC LIMIT 1",UUID.class,action,id);}
 Identity principal(Scope scope,Role role,Status status){return new Identity(UUID.randomUUID(),scope,scope==Scope.TENANT?tenant:null,role,status,"Principal");}



 Identity insert(Scope scope,UUID tenant,Role role,Status status,String name){var u=new Identity(UUID.randomUUID(),scope,tenant,role,status,name);new TransactionTemplate(tm).executeWithoutResult(s->identities.insert(u,actor));return u;}
 Identity own(String name){return insert(Scope.TENANT,tenant,Role.USER,Status.PENDING,name);}
 @Test void userCreatorLabelIsHistoricalAndIndependentOfCurrentActorLookup()throws Exception{
  var created=own("Snapshot target "+UUID.randomUUID());
  assertEquals(actor.displayName(),((Map<?,?>)data(request(admin,get("/api/v1/users/"+created.id()),null)).get("metadata")).get("createdByDisplayName"));
  assertEquals(actor.displayName(),((Map<?,?>)items(list(admin,Map.of("q",created.displayName()))).getFirst().get("metadata")).get("createdByDisplayName"));
 }
 String prefix(){return "users-"+UUID.randomUUID()+"-";}
 @SuppressWarnings("unchecked") List<Map<String,Object>> items(MvcResult r){return (List<Map<String,Object>>)data(r).get("items");}
 long total(MvcResult r){return ((Number)data(r).get("totalElements")).longValue();}
 MvcResult list(Identity who,Map<String,String> q)throws Exception{var b=get("/api/v1/users");q.forEach(b::param);var r=request(who,b,null);assertEquals(200,status(r),r.getResponse().getContentAsString());return r;}
 @Test void defaultsSizesBeyondEndAndCreationOrder()throws Exception{
  String p=prefix();for(int i=0;i<105;i++){clock.now=clock.now.plusSeconds(1);own(p+i);}
  var r=list(admin,Map.of("q",p));assertEquals(0,data(r).get("page"));assertEquals(20,data(r).get("size"));assertEquals(p+104,items(r).getFirst().get("displayName"));
  for(int size:List.of(1,10,20,30,40,50,100)){r=list(admin,Map.of("q",p,"size",String.valueOf(size)));assertEquals(size,items(r).size());assertEquals(105,total(r));}
  r=list(admin,Map.of("q",p,"size","100","page","2147483647"));assertTrue(items(r).isEmpty());assertEquals(105,total(r));assertEquals(2147483647,data(r).get("page"));assertEquals(0,total(list(admin,Map.of("q",prefix()))));
 }
 static java.util.stream.Stream<Arguments> invalidQueries(){return java.util.stream.Stream.of(
  new String[]{"page","-1"},new String[]{"page","2147483648"},new String[]{"size","0"},new String[]{"size","101"},new String[]{"size","bad"},new String[]{"sort","createdAt"},new String[]{"sort","tenantName"},new String[]{"sort","email"},new String[]{"sort","id"},new String[]{"sort","displayName;DROP TABLE app_user"},new String[]{"direction","desc"},new String[]{"direction","up"},new String[]{"scope","bad"},new String[]{"role","bad"},new String[]{"status","bad"},new String[]{"tenantId","bad"},new String[]{"email","text"},new String[]{"login","text"},new String[]{"deleted","all"},new String[]{"unpaged","true"}).map(v->Arguments.of(v[0],v[1]));}
 @ParameterizedTest @MethodSource("invalidQueries") void strictQuery(String field,String value)throws Exception{var r=request(admin,get("/api/v1/users").param(field,value),null);assertEquals(400,status(r));assertTrue(r.getResponse().getContentType().startsWith("application/problem+json"));}
 @Test void literalSearchAndSecretFieldsNeverParticipate()throws Exception{
  String p=prefix();own(p+"Literal%_\\Case");own(p+"LiteralXXCase");
  assertEquals(1,total(list(admin,Map.of("q","  "+p.toUpperCase()+"LITERAL%_\\  "))));assertEquals(2,total(list(admin,Map.of("q",p))));
  assertEquals(total(list(admin,Map.of())),total(list(admin,Map.of("q"," ".repeat(200)))));
  own("x".repeat(128));assertTrue(total(list(admin,Map.of("q"," "+"x".repeat(128)+" ")))>0);
  for(String q:List.of("x".repeat(129),"😀".repeat(65)))assertEquals(400,status(request(admin,get("/api/v1/users").param("q",q),null)));
 }
 @ParameterizedTest @ValueSource(strings={"displayName","scope","role","status"}) void fullResultSortAndCombinedPaging(String sort)throws Exception{
  String p=prefix();var expected=new HashSet<String>();for(int i=0;i<67;i++)expected.add(insert(i%2==0?Scope.GLOBAL:Scope.TENANT,i%2==0?null:tenant,i%3==0?Role.ADMIN:Role.USER,Status.values()[i%3],p+(char)('A'+i%3)).id().toString());
  for(String dir:List.of("asc","desc")){var seen=new HashSet<String>();String previous=null;for(int page=0;page<4;page++){var r=list(admin,Map.of("q",p,"sort",sort,"direction",dir,"page",String.valueOf(page),"size","20"));assertEquals(67,total(r));for(var row:items(r)){assertTrue(seen.add((String)row.get("id")));String value=(String)row.get(sort);if(previous!=null)assertTrue(dir.equals("asc")?previous.compareTo(value)<=0:previous.compareTo(value)>=0);previous=value;}}assertEquals(expected,seen);}
  var r=list(admin,Map.of("q",p,"scope","TENANT","tenantId",tenant.toString(),"role","USER","status","ACTIVE","sort",sort,"direction","desc","size","10"));assertTrue(total(r)>0);for(var row:items(r)){assertEquals("TENANT",row.get("scope"));assertEquals("USER",row.get("role"));assertEquals("ACTIVE",row.get("status"));assertEquals(tenant.toString(),row.get("tenantId"));}
 }
 @Test void tenantAdminForcedScopeAndIndependentDetail()throws Exception{
  String p=prefix();var allowed=own(p+"Own");var foreign=insert(Scope.TENANT,other,Role.USER,Status.PENDING,p+"Foreign");var elevated=insert(Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE,p+"Admin");var global=insert(Scope.GLOBAL,null,Role.USER,Status.ACTIVE,p+"Global");var who=principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE);
  for(UUID supplied:List.of(tenant,other,UUID.randomUUID())){var r=list(who,Map.of("q",p,"tenantId",supplied.toString(),"sort","role","direction","asc","size","1"));assertEquals(2,total(r));assertEquals(elevated.id().toString(),items(r).getFirst().get("id"));assertEquals(allowed.id().toString(),items(list(who,Map.of("q",p,"tenantId",supplied.toString(),"sort","role","direction","asc","size","1","page","1"))).getFirst().get("id"));}
  assertEquals(0,total(list(who,Map.of("q",p,"scope","GLOBAL"))));
  assertEquals(elevated.id().toString(),items(list(who,Map.of("q",p,"role","ADMIN"))).getFirst().get("id"));
  assertEquals(1,total(list(who,Map.of("q",p,"scope","TENANT","role","USER","status","PENDING"))));
  for(UUID id:List.of(foreign.id(),global.id(),UUID.randomUUID())){var r=request(who,get("/api/v1/users/"+id),null);assertEquals(404,status(r));assertEquals("User not found in administrative scope",data(r).get("detail"));}
  assertEquals("ADMIN",data(request(who,get("/api/v1/users/"+elevated.id()),null)).get("role"));
  assertEquals(404,status(request(who,post("/api/v1/users/"+elevated.id()+"/disable"),null)));
  assertEquals(403,status(request(who,put("/api/v1/users/"+elevated.id()+"/role"),Map.of("role","USER"))));
  var d=data(request(who,get("/api/v1/users/"+allowed.id()),null));assertEquals(allowed.id().toString(),d.get("id"));assertEquals(tenant.toString(),((Map<?,?>)d.get("tenant")).get("id"));assertNotNull(((Map<?,?>)d.get("tenant")).get("name"));
  assertEquals(404,status(request(admin,get("/api/v1/users").param("tenantId",UUID.randomUUID().toString()),null)));
  assertEquals(1,total(list(admin,Map.of("q",p,"tenantId",other.toString()))));assertEquals(4,total(list(admin,Map.of("q",p))));
 }
 @ParameterizedTest @CsvSource({"GLOBAL,ADMIN,ACTIVE,200","TENANT,ADMIN,ACTIVE,200","GLOBAL,USER,ACTIVE,403","TENANT,USER,ACTIVE,403","GLOBAL,ADMIN,PENDING,403","TENANT,ADMIN,PENDING,403","GLOBAL,ADMIN,DISABLED,403","TENANT,ADMIN,DISABLED,403"}) void scopeRoleStatusMatrix(Scope scope,Role role,Status state,int result)throws Exception{
  var u=own(prefix());var who=principal(scope,role,state);if(state==Status.DISABLED){assertThrows(AuthFailure.class,()->tokens.login(who));assertEquals(403,assertThrows(AuthFailure.class,()->auth.list(who,new com.github.ksewen.kelios.identity.UserQuery(0,20,null,null,null,null,null,null,null))).status());assertEquals(403,assertThrows(AuthFailure.class,()->auth.detail(who,u.id())).status());return;}assertEquals(result,status(request(who,get("/api/v1/users"),null)));assertEquals(result,status(request(who,get("/api/v1/users/"+u.id()),null)));
 }
 @Test void safeDetailReadDoesNotAuditAndIsIndependentOfCollectionPage()throws Exception{
  var u=own(prefix());long before=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
  assertTrue(items(list(admin,Map.of("q","absent"))).isEmpty());var r=request(admin,get("/api/v1/users/"+u.id()),null);assertEquals(200,status(r));var d=data(r);assertEquals(Set.of("id","scope","tenantId","role","status","displayName","tenant","metadata","globalAccount"),d.keySet());
  assertEquals(actor.id().toString(),((Map<?,?>)d.get("metadata")).get("createdBy"));assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  var g=insert(Scope.GLOBAL,null,Role.USER,Status.ACTIVE,prefix());assertNull(data(request(admin,get("/api/v1/users/"+g.id()),null)).get("tenant"));
  assertEquals(401,status(request(null,get("/api/v1/users"),null)));assertEquals(401,status(request(null,get("/api/v1/users/"+g.id()),null)));assertEquals(405,status(request(admin,delete("/api/v1/users/"+u.id()),null)));
 }
 @ParameterizedTest @ValueSource(strings={"createdBy","createdAt","updatedBy","updatedAt","tenantId","scope","role","status","login"}) void strictGlobalCommandOverposting(String key)throws Exception{
  var body=new LinkedHashMap<String,Object>(Map.of("email","step6-"+UUID.randomUUID()+"@example.org","displayName","User","password","CorrectHorse25!Battery"));body.put(key,"forged");assertEquals(400,status(request(admin,post("/api/v1/users/global"),body)));
 }
 @Test void existingCommandsAndMetadataAuditAtomicity()throws Exception{
  var u=own(prefix());var who=principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE);clock.now=clock.now.plusSeconds(20);
  var r=request(who,post("/api/v1/users/"+u.id()+"/approve"),null);assertEquals(200,status(r));assertEquals("ACTIVE",data(r).get("status"));var d=data(request(who,get("/api/v1/users/"+u.id()),null));var metadata=(Map<?,?>)d.get("metadata");assertEquals(actor.id().toString(),metadata.get("createdBy"));assertEquals(clock.now.minusSeconds(20).toString(),metadata.get("createdAt"));assertEquals(who.id().toString(),metadata.get("updatedBy"));assertEquals(clock.now.toString(),metadata.get("updatedAt"));assertEquals(tenant,attribution("USER_APPROVED",u.id()));
  var tx=new TransactionTemplate(tm);assertThrows(IllegalStateException.class,()->tx.execute(s->{auth.disable(who,u.id());throw new IllegalStateException("rollback");}));assertEquals(Status.ACTIVE,identities.load(u.id(),"").status());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action='USER_DISABLED'",Integer.class,u.id()));
  assertEquals(403,status(request(who,put("/api/v1/users/"+u.id()+"/role"),Map.of("role","ADMIN"))));
  r=request(admin,post("/api/v1/users/global"),Map.of("email","step6-global-"+UUID.randomUUID()+"@example.org","displayName","Global new","password","CorrectHorse25!Battery"));assertEquals(200,status(r));assertEquals("GLOBAL",data(r).get("scope"));assertEquals("USER",data(r).get("role"));assertEquals("ACTIVE",data(r).get("status"));assertFalse(data(r).containsKey("login"));UUID id=UUID.fromString((String)data(r).get("id"));
  assertEquals(200,status(request(admin,put("/api/v1/users/"+id+"/role"),Map.of("role","ADMIN"))));assertEquals(200,status(request(admin,put("/api/v1/users/"+id+"/local-credential"),Map.of("email","step6-reset-"+UUID.randomUUID()+"@example.org","password","CorrectHorse25!Battery"))));
  assertEquals(403,status(request(admin,post("/api/v1/users/"+admin.id()+"/disable"),null)));assertEquals(403,status(request(admin,put("/api/v1/users/"+admin.id()+"/role"),Map.of("role","USER"))));
 }
 @Test void strictRoleAndCredentialCommands()throws Exception{
  var u=own(prefix());for(Object role:List.of(1,true,"notRole"))assertEquals(400,status(request(admin,put("/api/v1/users/"+u.id()+"/role"),Map.of("role",role))));
  assertEquals(400,status(request(admin,put("/api/v1/users/"+u.id()+"/role"),Map.of("role","ADMIN","updatedBy",actor.id()))));
  assertEquals(400,status(request(admin,put("/api/v1/users/"+u.id()+"/local-credential"),Map.of("email","bad@example.org","password","CorrectHorse25!Battery","actorId",actor.id()))));
  assertEquals(403,status(request(admin,put("/api/v1/users/"+u.id()+"/local-credential"),Map.of("email","tenant@example.org","password","CorrectHorse25!Battery"))));assertEquals(Status.PENDING,identities.load(u.id(),"").status());
 }
 @Test void failedAuditRollsBackUserCredentialAndMetadata(){
  String login="audit-"+UUID.randomUUID()+"@example.org";
  jdbc.execute("CREATE FUNCTION step6_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='USER_CREATED' THEN RAISE EXCEPTION 'isolated audit failure'; END IF; RETURN NEW; END; $$");
  jdbc.execute("CREATE TRIGGER step6_reject_audit BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION step6_reject_audit()");
  try{assertThrows(RuntimeException.class,()->auth.createGlobal(admin,login,"Rollback User","CorrectHorse25!Battery",Role.ADMIN));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM local_global_credential WHERE email=?",Integer.class,login));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM app_user WHERE display_name='Rollback User'",Integer.class));}
  finally{jdbc.execute("DROP TRIGGER step6_reject_audit ON audit_log");jdbc.execute("DROP FUNCTION step6_reject_audit()");}
 }

 @Test void userListEmailIsGlobalOnlyAndNeverExposesCredentials()throws Exception{
  String p=prefix(),email=p+"global@example.org";
  var global=auth.createGlobal(admin,email,p+"Global","CorrectHorse25!Battery");var local=own(p+"Tenant");
  var rows=items(list(admin,Map.of("q",p)));assertEquals(2,rows.size());
  var g=rows.stream().filter(r->global.id().toString().equals(r.get("id"))).findFirst().orElseThrow();
  var t=rows.stream().filter(r->local.id().toString().equals(r.get("id"))).findFirst().orElseThrow();
  assertEquals(email,g.get("email"));assertNull(t.get("email"));
  assertEquals(Set.of("id","scope","tenantId","role","status","displayName","email","tenant","metadata"),g.keySet());
  String serialized=json.writeValueAsString(rows);for(String secret:List.of("password","hash","verifiedAt","subject","claim"))assertFalse(serialized.contains(secret));
  assertNull(items(list(principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE),Map.of("q",p))).getFirst().get("email"));
 }
 @Test void atomicCreateRoleDefaultsAndAdminIsAudited()throws Exception{
  String p=prefix();var basic=request(admin,post("/api/v1/users/global"),Map.of("email",p+"default@example.org","displayName",p+"Default","password","CorrectHorse25!Battery"));
  assertEquals(200,status(basic));assertEquals("USER",data(basic).get("role"));
  var elevated=request(admin,post("/api/v1/users/global"),Map.of("email",p+"admin@example.org","displayName",p+"Admin","password","CorrectHorse25!Battery","role","ADMIN"));
  assertEquals(200,status(elevated));UUID id=UUID.fromString((String)data(elevated).get("id"));assertEquals("ADMIN",identities.load(id,"").role().name());
  assertEquals("ADMIN",jdbc.queryForObject("SELECT data->>'role' FROM audit_log WHERE action='USER_CREATED' AND target_id=?",String.class,id));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM local_global_credential WHERE user_id=?",Integer.class,id));
  for(Identity denied:List.of(principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE),principal(Scope.GLOBAL,Role.USER,Status.ACTIVE)))assertEquals(403,status(request(denied,post("/api/v1/users/global"),Map.of("email",p+UUID.randomUUID()+"@example.org","displayName","Denied","password","CorrectHorse25!Battery","role","ADMIN"))));
  for(Object role:List.of("ROOT",1,true))assertEquals(400,status(request(admin,post("/api/v1/users/global"),Map.of("email",p+UUID.randomUUID()+"@example.org","displayName","Invalid","password","CorrectHorse25!Battery","role",role))));
 }
 @Test void combinedAdministrationChangesOnlyRequestedFieldsAndPreservesGuards()throws Exception{
  String p=prefix();var u=auth.createGlobal(admin,p+"before@example.org",p+"Target","CorrectHorse25!Battery");clock.now=clock.now.plusSeconds(1);
  UUID id=u.id();var path="/api/v1/users/"+id+"/administration";
  var changed=request(admin,put(path),Map.of("email",p+"after@example.org","role","ADMIN"));assertEquals(200,status(changed));
  assertEquals("ADMIN",data(changed).get("role"));assertEquals(p+"after@example.org",auth.detail(admin,id).globalAccount().email());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='USER_ROLE_CHANGED' AND target_id=?",Integer.class,id));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='GLOBAL_EMAIL_CHANGED' AND target_id=?",Integer.class,id));
  assertFalse(jdbc.queryForObject("SELECT data::text FROM audit_log WHERE action='GLOBAL_EMAIL_CHANGED' AND target_id=?",String.class,id).contains("@"));
  var previous=jdbc.queryForMap("SELECT * FROM app_user WHERE id=?",id);
  assertEquals(200,status(request(admin,put(path),Map.of("email",p+"last@example.org"))));assertEquals(previous.get("role"),jdbc.queryForMap("SELECT * FROM app_user WHERE id=?",id).get("role"));
  assertEquals(409,status(request(admin,put(path),Map.of("role","ADMIN"))));
  var local=own(p+"Local");assertEquals(403,status(request(admin,put("/api/v1/users/"+local.id()+"/administration"),Map.of("email",p+"tenant@example.org"))));
  assertEquals(403,status(request(admin,put("/api/v1/users/"+admin.id()+"/administration"),Map.of("role","USER"))));
  for(Identity denied:List.of(principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE),principal(Scope.GLOBAL,Role.USER,Status.ACTIVE),principal(Scope.GLOBAL,Role.ADMIN,Status.PENDING)))assertEquals(403,status(request(denied,put(path),Map.of("role","USER"))));
  for(Map<String,Object> body:List.of(Map.<String,Object>of(),Map.<String,Object>of("displayName","Forged"),Map.<String,Object>of("email",p+"forged@example.org","status","ACTIVE"),Map.<String,Object>of("role","ROOT")))assertEquals(400,status(request(admin,put(path),body)));
 }
 @Test void combinedAdministrationAuditFailureRollsBackBothFields()throws Exception{
  String p=prefix();var u=auth.createGlobal(admin,p+"before@example.org",p+"Rollback","CorrectHorse25!Battery");
  var beforeUser=jdbc.queryForMap("SELECT * FROM app_user WHERE id=?",u.id());var beforeCredential=jdbc.queryForMap("SELECT * FROM local_global_credential WHERE user_id=?",u.id());
  jdbc.execute("CREATE FUNCTION g2_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='GLOBAL_EMAIL_CHANGED' THEN RAISE EXCEPTION 'isolated audit failure'; END IF; RETURN NEW; END; $$");
  jdbc.execute("CREATE TRIGGER g2_reject_audit BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION g2_reject_audit()");
  try{assertThrows(RuntimeException.class,()->auth.administration(admin,u.id(),p+"after@example.org",Role.ADMIN));assertEquals(beforeUser,jdbc.queryForMap("SELECT * FROM app_user WHERE id=?",u.id()));assertEquals(beforeCredential,jdbc.queryForMap("SELECT * FROM local_global_credential WHERE user_id=?",u.id()));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action IN ('USER_ROLE_CHANGED','GLOBAL_EMAIL_CHANGED') AND target_id=?",Integer.class,u.id()));}
  finally{jdbc.execute("DROP TRIGGER g2_reject_audit ON audit_log");jdbc.execute("DROP FUNCTION g2_reject_audit()");}
 }
 @Test void concurrentCombinedEditsSerializeAndRejectStaleDuplicate()throws Exception{
  String p=prefix();var u=auth.createGlobal(admin,p+"before@example.org",p+"Concurrent","CorrectHorse25!Battery");var start=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   var tasks=new ArrayList<Future<Integer>>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->{start.await();try{auth.administration(admin,u.id(),p+"after@example.org",Role.ADMIN);return 200;}catch(AuthFailure e){return e.status();}}));start.countDown();
   var outcomes=List.of(tasks.get(0).get(20,TimeUnit.SECONDS),tasks.get(1).get(20,TimeUnit.SECONDS));assertTrue(outcomes.containsAll(List.of(200,409)),outcomes.toString());
  }
  assertEquals(Role.ADMIN,identities.load(u.id(),"").role());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='USER_ROLE_CHANGED' AND target_id=?",Integer.class,u.id()));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='GLOBAL_EMAIL_CHANGED' AND target_id=?",Integer.class,u.id()));
 }

}
