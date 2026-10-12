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
class TenantResourceIT {
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
 static final AuthTestKeys KEYS;static{try{KEYS=new AuthTestKeys("tenant-resource-keys");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",PG::getJdbcUrl);p.add("spring.datasource.username",PG::getUsername);p.add("spring.datasource.password",PG::getPassword);p.add("kelios.auth.key-directory",()->KEYS.directory.toAbsolutePath().toString());p.add("kelios.auth.public-origin",()->"https://kelios.example");p.add("kelios.auth.issuer",()->"https://kelios.example");}
 @Autowired TenantService tenants;
 @Autowired JwtTokens tokens;
 @Autowired JdbcTemplate jdbc;@Autowired WebApplicationContext context;
 final JsonMapper json=JsonMapper.builder().build();final Actor actor=new Actor(UUID.randomUUID(),"Global historical actor");
 MockMvc mvc;UUID tenant,other;Identity admin;
 @BeforeEach void setup(){clock.now=Instant.parse("2026-10-11T12:00:00Z");tenant=tenants.create("Tenant "+UUID.randomUUID(),actor);other=tenants.create("Other",actor);admin=new Identity(actor.id(),Scope.GLOBAL,null,Role.ADMIN,Status.ACTIVE,actor.displayName());mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 MvcResult request(Identity who,MockHttpServletRequestBuilder b,Object body)throws Exception{if(who!=null)b.header("Authorization","Bearer "+tokens.login(who).accessToken());if(body!=null)b.contentType("application/json").content(json.writeValueAsString(body));return mvc.perform(b).andReturn();}
 Map<String,Object> data(MvcResult r){return json.readValue(r.getResponse().getContentAsByteArray(),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});}
 int status(MvcResult r){return r.getResponse().getStatus();}
 UUID attribution(String action,UUID id){return jdbc.queryForObject("SELECT tenant_id FROM audit_log WHERE action=? AND target_id=? ORDER BY occurred_at DESC,id DESC LIMIT 1",UUID.class,action,id);}
 Identity principal(Scope scope,Role role,Status status){return new Identity(UUID.randomUUID(),scope,scope==Scope.TENANT?tenant:null,role,status,"Principal");}




 @Autowired com.github.ksewen.kelios.tenancy.TenantOperations operations;
 void principal(Identity who){org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(who,null,List.of()));}
 @AfterEach void clear(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 @Test void independentUuidDetailSafeProjectionAndNoReadAudit()throws Exception{
  long before=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
  var who=new Identity(UUID.randomUUID(),Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE,"Reader");
  var r=request(who,get("/api/v1/tenants/"+tenant),null);assertEquals(200,status(r));var d=data(r);assertEquals(Set.of("id","name","metadata"),d.keySet());assertEquals(tenant.toString(),d.get("id"));assertEquals(actor.id().toString(),((Map<?,?>)d.get("metadata")).get("createdBy"));
  assertEquals(403,status(request(who,get("/api/v1/tenants"),null)));
  var list=request(admin,get("/api/v1/tenants"),null);var items=json.readValue(list.getResponse().getContentAsString(),List.class);assertTrue(items.contains(d));
  assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  for(String forbidden:List.of("email","password","issuer","clientSecret","allowedGroup","scope","role"))assertFalse(r.getResponse().getContentAsString().contains(forbidden));
 }
 @Test void foreignAndAbsentAreIndistinguishableAndTransportCannotExpandTenantScope()throws Exception{
  var who=new Identity(UUID.randomUUID(),Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE,"Admin");
  var foreign=request(who,get("/api/v1/tenants/"+other).param("tenantId",tenant.toString()),null);var missing=request(who,get("/api/v1/tenants/"+UUID.randomUUID()),null);assertEquals(404,status(foreign));assertEquals(404,status(missing));assertEquals(data(foreign).get("detail"),data(missing).get("detail"));
  assertEquals(200,status(request(admin,get("/api/v1/tenants/"+other),null)));
  assertEquals(401,status(request(null,get("/api/v1/tenants/"+tenant),null)));assertEquals(400,status(request(admin,get("/api/v1/tenants/bad-uuid"),null)));
 }
 @ParameterizedTest @CsvSource({"GLOBAL,ADMIN,ACTIVE","GLOBAL,USER,ACTIVE","TENANT,ADMIN,ACTIVE","TENANT,USER,ACTIVE","GLOBAL,ADMIN,PENDING","GLOBAL,USER,PENDING","TENANT,ADMIN,PENDING","TENANT,USER,PENDING","GLOBAL,ADMIN,DISABLED","GLOBAL,USER,DISABLED","TENANT,ADMIN,DISABLED","TENANT,USER,DISABLED"})
 void authoritativeServiceScopeAndStatus(Scope scope,Role role,Status state){
  var who=new Identity(UUID.randomUUID(),scope,scope==Scope.TENANT?tenant:null,role,state,"Actor");principal(who);
  if(state!=Status.ACTIVE || role!=Role.ADMIN){assertEquals(403,assertThrows(AuthFailure.class,operations::list).status());assertEquals(403,assertThrows(AuthFailure.class,()->operations.detail(tenant)).status());assertEquals(403,assertThrows(AuthFailure.class,()->operations.create("Denied")).status());return;}
  assertEquals(tenant,operations.detail(tenant).id());
  if(scope==Scope.TENANT){assertEquals(403,assertThrows(AuthFailure.class,operations::list).status());assertEquals(404,assertThrows(AuthFailure.class,()->operations.detail(other)).status());}
  else assertTrue(operations.list().stream().anyMatch(t->t.id().equals(other)));
  if(scope==Scope.GLOBAL && role==Role.ADMIN)assertNotNull(operations.create("Authorized"));else assertEquals(403,assertThrows(AuthFailure.class,()->operations.create("Denied")).status());
 }
 @Test void collectionIsIntentionallyUnpagedWithCreatedAtDefault()throws Exception{
  for(int i=0;i<105;i++){clock.now=clock.now.plusSeconds(1);tenants.create("Tenant "+i,actor);}clock.now=clock.now.plusSeconds(1);UUID last=tenants.create("Newest",actor);
  var r=request(admin,get("/api/v1/tenants"),null);assertEquals(200,status(r));var items=json.readValue(r.getResponse().getContentAsString(),List.class);assertTrue(items.size()>100);assertEquals(last.toString(),((Map<?,?>)items.getFirst()).get("id"));
 }
 @ParameterizedTest @ValueSource(strings={"page","size","q","sort","direction","deleted","tenantId","unpaged"}) void noInventedTenantCollectionParameters(String key)throws Exception{assertEquals(400,status(request(admin,get("/api/v1/tenants").param(key,"anything"),null)));}
 @ParameterizedTest @ValueSource(strings={"createdBy","createdAt","updatedBy","updatedAt","deletedAt","id","tenantId","issuer","email"}) void creationRejectsOverposting(String key)throws Exception{var b=new LinkedHashMap<String,Object>(Map.of("name","Overpost"));b.put(key,"forged");assertEquals(400,status(request(admin,post("/api/v1/tenants"),b)));}
 @Test void createRetainsUuidResponseMetadataAndAtomicAudit()throws Exception{
  var r=request(admin,post("/api/v1/tenants"),Map.of("name","  School  "));assertEquals(200,status(r));UUID id=UUID.fromString(json.readValue(r.getResponse().getContentAsString(),String.class));
  var row=jdbc.queryForMap("SELECT * FROM tenant WHERE id=?",id);assertEquals("School",row.get("name"));assertEquals(actor.id(),row.get("created_by"));assertEquals(row.get("created_by"),row.get("updated_by"));assertEquals(row.get("created_at"),row.get("updated_at"));assertEquals(clock.instant(),((java.sql.Timestamp)row.get("created_at")).toInstant());
  assertEquals(id,attribution("TENANT_CREATED",id));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='TENANT_CREATED' AND target_id=? AND actor_id=?",Integer.class,id,actor.id()));
  assertEquals(200,status(request(admin,post("/api/v1/tenants"),Map.of("name","School"))));
  for(Object b:List.of(Map.of("name"," "),Map.of(),Map.of("name",23)))assertEquals(400,status(request(admin,post("/api/v1/tenants"),b)));
 }
 @Test void auditFailureRollsBackCreation(){
  long count=jdbc.queryForObject("SELECT count(*) FROM tenant",Long.class);principal(admin);
  jdbc.execute("CREATE FUNCTION step7_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='TENANT_CREATED' THEN RAISE EXCEPTION 'isolated audit failure'; END IF; RETURN NEW; END; $$");jdbc.execute("CREATE TRIGGER step7_reject_audit BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION step7_reject_audit()");
  try{assertThrows(RuntimeException.class,()->operations.create("Rollback"));assertEquals(count,jdbc.queryForObject("SELECT count(*) FROM tenant",Long.class));}
  finally{jdbc.execute("DROP TRIGGER step7_reject_audit ON audit_log");jdbc.execute("DROP FUNCTION step7_reject_audit()");}
 }
 @Test void undefinedMutationsAreNotExposed()throws Exception{for(var b:List.of(put("/api/v1/tenants/"+tenant),patch("/api/v1/tenants/"+tenant),delete("/api/v1/tenants/"+tenant)))assertEquals(405,status(request(admin,b,Map.of("name","No mutation"))));}

 @ParameterizedTest @CsvSource({"GLOBAL,ADMIN,ACTIVE,200,200,200","TENANT,ADMIN,ACTIVE,403,200,403","GLOBAL,USER,ACTIVE,403,403,403","TENANT,USER,ACTIVE,403,403,403","GLOBAL,ADMIN,PENDING,403,403,403","TENANT,ADMIN,PENDING,403,403,403","GLOBAL,USER,PENDING,403,403,403","TENANT,USER,PENDING,403,403,403"})
 void revisedHttpAdministrationMatrix(Scope scope,Role role,Status state,int listStatus,int detailStatus,int createStatus)throws Exception{
  var who=new Identity(UUID.randomUUID(),scope,scope==Scope.TENANT?tenant:null,role,state,"Principal");
  assertEquals(listStatus,status(request(who,get("/api/v1/tenants"),null)));
  assertEquals(detailStatus,status(request(who,get("/api/v1/tenants/"+tenant),null)));
  long before=jdbc.queryForObject("SELECT count(*) FROM tenant",Long.class);
  assertEquals(createStatus,status(request(who,post("/api/v1/tenants"),Map.of("name","Policy create"))));
  assertEquals(before+(createStatus==200?1:0),jdbc.queryForObject("SELECT count(*) FROM tenant",Long.class));
  if(scope==Scope.TENANT && role==Role.ADMIN && state==Status.ACTIVE)assertEquals(404,status(request(who,get("/api/v1/tenants/"+other).param("tenantId",tenant.toString()),null)));
 }
}
