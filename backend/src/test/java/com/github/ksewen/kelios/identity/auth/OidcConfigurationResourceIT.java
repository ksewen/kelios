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
class OidcConfigurationResourceIT {
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
 static final AuthTestKeys KEYS;static{try{KEYS=new AuthTestKeys("oidc-resource-keys");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
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





 @Autowired OidcConfigurationService configurations;@Autowired SecretEnvelope secrets;
 Map<String,Object> input(){return new LinkedHashMap<>(Map.of("issuer","https://fixture.example","clientId","client","clientSecret","isolated-secret","enabled",true,"allowedGroupIds",List.of(UUID.randomUUID().toString())));}
 OidcConfigurationService.View configure(){return configurations.put(admin,tenant,"https://fixture.example","client","isolated-secret",true,null,Set.of(UUID.randomUUID()));}
 @Test void globalConfigurationCollectionIncludesDisabledAndOnlySafeFieldsInStablePages()throws Exception{
  UUID alpha=tenants.create("A configuration "+UUID.randomUUID(),actor);
  configurations.put(admin,alpha,"https://alpha.example","alpha-client","alpha-secret",true,null,Set.of(UUID.randomUUID()));
  configurations.put(admin,other,"https://other.example","other-client","other-secret",false,null,Set.of(UUID.randomUUID()));
  long count=jdbc.queryForObject("SELECT count(*) FROM tenant_oidc_config",Long.class);
  assertEquals(20,data(request(admin,get("/api/v1/oidc-configurations"),null)).get("size"));
  var expected=jdbc.queryForList("SELECT p.tenant_id FROM tenant_oidc_config p JOIN tenant t ON t.id=p.tenant_id ORDER BY lower(t.name),t.id",UUID.class).stream().map(UUID::toString).toList();
  List<String> seen=new ArrayList<>();for(int page=0;page<count;page++){
   var response=request(admin,get("/api/v1/oidc-configurations").param("page",String.valueOf(page)).param("size","1"),null);
   assertEquals(200,status(response));assertEquals("no-store",response.getResponse().getHeader("Cache-Control"));var body=data(response);
   assertEquals(Set.of("items","page","size","totalElements"),body.keySet());assertEquals(count,((Number)body.get("totalElements")).longValue());assertEquals(page,body.get("page"));assertEquals(1,body.get("size"));
   var item=(Map<?,?>)((List<?>)body.get("items")).getFirst();assertEquals(Set.of("tenantId","tenant","issuer","clientId","enabled","version"),item.keySet());assertEquals(Set.of("id","name"),((Map<?,?>)item.get("tenant")).keySet());seen.add((String)item.get("tenantId"));
   String serialized=response.getResponse().getContentAsString();for(String forbidden:List.of("alpha-secret","other-secret","encrypted","allowedGroup","claim","callbackUri","secretConfigured","metadata"))assertFalse(serialized.contains(forbidden));
  }
  assertEquals(expected,seen);assertTrue(seen.contains(other.toString()));assertEquals(false,((Map<?,?>)((List<?>)data(request(admin,get("/api/v1/oidc-configurations").param("page",String.valueOf(seen.indexOf(other.toString()))).param("size","1"),null)).get("items")).getFirst()).get("enabled"));
  assertTrue(((List<?>)data(request(admin,get("/api/v1/oidc-configurations").param("page",String.valueOf(count)).param("size","1"),null)).get("items")).isEmpty());
  assertEquals(400,status(request(admin,get("/api/v1/oidc-configurations").param("q","alpha"),null)));
  assertEquals(400,status(request(admin,get("/api/v1/oidc-configurations").param("size","101"),null)));
 }
 @Test void configurationCollectionAuthorityDoesNotExpandSingletonOrMutation()throws Exception{
  configure();configurations.put(admin,other,"https://other.example","other-client","other-secret",false,null,Set.of());
  assertEquals(401,status(request(null,get("/api/v1/oidc-configurations"),null)));
  for(Scope scope:Scope.values())for(Role role:Role.values())for(Status state:Status.values()){
   var who=principal(scope,role,state);int expected=scope==Scope.GLOBAL&&role==Role.ADMIN&&state==Status.ACTIVE?200:403;
   if(state!=Status.DISABLED)assertEquals(expected,status(request(who,get("/api/v1/oidc-configurations"),null)),scope+" "+role+" "+state);
   if(expected==403)assertEquals(403,assertThrows(AuthFailure.class,()->configurations.configurations(who,0,20)).status());
  }
  var own=principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE);
  assertEquals(200,status(request(own,get("/api/v1/tenants/"+tenant+"/oidc"),null)));
  assertEquals(404,status(request(own,get("/api/v1/tenants/"+other+"/oidc"),null)));
  assertEquals(404,status(request(own,put("/api/v1/tenants/"+other+"/oidc"),input())));
  assertEquals(404,status(request(own,delete("/api/v1/tenants/"+other+"/oidc").param("version","0"),null)));
  assertEquals(403,status(request(principal(Scope.GLOBAL,Role.USER,Status.ACTIVE),put("/api/v1/tenants/"+tenant+"/oidc"),input())));
  assertEquals(403,status(request(principal(Scope.GLOBAL,Role.USER,Status.ACTIVE),delete("/api/v1/tenants/"+tenant+"/oidc").param("version","0"),null)));
  assertEquals(405,status(request(admin,put("/api/v1/oidc-configurations"),input())));
 }
 @Test void independentSingletonDetailHasReadableTenantAndNoSecretProjection()throws Exception{
  assertEquals(404,status(request(admin,get("/api/v1/tenants/"+tenant+"/oidc"),null)));assertEquals(404,status(request(admin,get("/api/v1/tenants/"+UUID.randomUUID()+"/oidc"),null)));assertEquals(401,status(request(null,get("/api/v1/tenants/"+tenant+"/oidc"),null)));
  var created=request(admin,put("/api/v1/tenants/"+tenant+"/oidc"),input());assertEquals(200,status(created));long count=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
  var detail=request(admin,get("/api/v1/tenants/"+tenant+"/oidc"),null);assertEquals(200,status(detail));var d=data(detail);assertEquals(Set.of("tenantId","issuer","clientId","secretConfigured","enabled","version","allowedGroupIds","callbackUri","metadata","tenant"),d.keySet());assertEquals(tenant.toString(),((Map<?,?>)d.get("tenant")).get("id"));assertTrue(((String)((Map<?,?>)d.get("tenant")).get("name")).startsWith("Tenant "));assertEquals(created.getResponse().getContentAsString(),detail.getResponse().getContentAsString());
  String cipher=jdbc.queryForObject("SELECT encrypted_client_secret FROM tenant_oidc_config WHERE tenant_id=?",String.class,tenant);String body=detail.getResponse().getContentAsString();for(String forbidden:List.of("isolated-secret",cipher,"clientSecret","encryptedSecret","password","emailVerifiedAt","claims"))assertFalse(body.contains(forbidden));assertEquals(count,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));assertEquals("https://kelios.example/api/v1/auth/oidc/"+tenant+"/callback",d.get("callbackUri"));
 }
 @ParameterizedTest @CsvSource({"GLOBAL,ADMIN,ACTIVE","GLOBAL,USER,ACTIVE","TENANT,ADMIN,ACTIVE","TENANT,USER,ACTIVE","GLOBAL,ADMIN,PENDING","GLOBAL,USER,PENDING","TENANT,ADMIN,PENDING","TENANT,USER,PENDING","GLOBAL,ADMIN,DISABLED","GLOBAL,USER,DISABLED","TENANT,ADMIN,DISABLED","TENANT,USER,DISABLED"})
 void revisedServiceAuthority(Scope scope,Role role,Status state){
  configure();var who=new Identity(UUID.randomUUID(),scope,scope==Scope.TENANT?tenant:null,role,state,"Actor");
  if(role==Role.ADMIN && state==Status.ACTIVE){
   assertEquals(tenant,configurations.get(who,tenant).tenant().id());
   var updated=configurations.put(who,tenant,"https://fixture.example","client",null,true,0L,Set.of());assertEquals(who.id(),updated.metadata().updatedBy());
   if(scope==Scope.GLOBAL)assertDoesNotThrow(()->configurations.put(who,other,"https://fixture.example","client","secret",true,null,Set.of()));
   else{assertEquals(404,assertThrows(AuthFailure.class,()->configurations.get(who,other)).status());assertEquals(404,assertThrows(AuthFailure.class,()->configurations.put(who,other,"https://fixture.example","client","secret",true,null,Set.of())).status());assertEquals(404,assertThrows(AuthFailure.class,()->configurations.delete(who,other,0)).status());}
   configurations.delete(who,tenant,1);
  }else{assertEquals(403,assertThrows(AuthFailure.class,()->configurations.get(who,tenant)).status());assertEquals(403,assertThrows(AuthFailure.class,()->configurations.get(who,other)).status());assertEquals(403,assertThrows(AuthFailure.class,()->configurations.put(who,tenant,"https://fixture.example","client","secret",true,null,Set.of())).status());assertEquals(403,assertThrows(AuthFailure.class,()->configurations.delete(who,tenant,0)).status());}
 }
 @ParameterizedTest @ValueSource(strings={"actorId","tenantId","createdBy","createdAt","updatedBy","updatedAt","secretConfigured","scope","role","email","callbackUri","claims"}) void strictConfigurationRejectsOverposting(String field)throws Exception{var b=input();b.put(field,"forged");assertEquals(400,status(request(admin,put("/api/v1/tenants/"+tenant+"/oidc"),b)));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM tenant_oidc_config WHERE tenant_id=?",Integer.class,tenant));}
 @ParameterizedTest @ValueSource(strings={"missingEnabled","stringEnabled","invalidVersion","negativeVersion","invalidGroups","nullGroup","badUuid","missingSecret"}) void strictMalformedInput(String kind)throws Exception{var b=input();switch(kind){case "missingEnabled"->b.remove("enabled");case "stringEnabled"->b.put("enabled","true");case "invalidVersion"->b.put("version",1.5);case "negativeVersion"->b.put("version",-1);case "invalidGroups"->b.put("allowedGroupIds","not-list");case "nullGroup"->b.put("allowedGroupIds",Arrays.asList((Object)null));case "badUuid"->b.put("allowedGroupIds",List.of("1-1-1-1-1"));case "missingSecret"->b.remove("clientSecret");}assertEquals(400,status(request(admin,put("/api/v1/tenants/"+tenant+"/oidc"),b)));}
 @Test void updatePreservesCreationAndRetainsReencryptsSecretWithVersionedGroups(){var initial=configure();var before=jdbc.queryForMap("SELECT * FROM tenant_oidc_config WHERE tenant_id=?",tenant);clock.now=clock.now.plusSeconds(1);var changed=configurations.put(admin,tenant,"https://other-fixture.example","changed-client",null,false,initial.version(),Set.of());assertEquals(1,changed.version());assertFalse(changed.enabled());assertTrue(changed.allowedGroupIds().isEmpty());var after=jdbc.queryForMap("SELECT * FROM tenant_oidc_config WHERE tenant_id=?",tenant);assertEquals(before.get("created_by"),after.get("created_by"));assertEquals(before.get("created_at"),after.get("created_at"));assertNotEquals(before.get("updated_at"),after.get("updated_at"));assertEquals(actor.id(),after.get("updated_by"));assertNotEquals(before.get("encrypted_client_secret"),after.get("encrypted_client_secret"));assertEquals("isolated-secret",secrets.decrypt((String)after.get("encrypted_client_secret"),OidcConfigurationService.context(tenant,"https://other-fixture.example","changed-client")));assertEquals(403,assertThrows(AuthFailure.class,()->configurations.provider(tenant)).status());assertEquals(409,assertThrows(AuthFailure.class,()->configurations.put(admin,tenant,"https://fixture.example","client",null,true,0L,Set.of())).status());assertEquals(tenant,attribution("OIDC_CONFIG_CHANGED",tenant));}
 @Test void auditFailureRollsBackSettingsSecretGroupsAndMetadata(){configure();var before=jdbc.queryForMap("SELECT * FROM tenant_oidc_config WHERE tenant_id=?",tenant);var groups=jdbc.queryForList("SELECT group_id FROM tenant_oidc_allowed_group WHERE tenant_id=? ORDER BY group_id",UUID.class,tenant);long count=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);jdbc.execute("CREATE FUNCTION step8_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='OIDC_CONFIG_CHANGED' THEN RAISE EXCEPTION 'isolated audit failure'; END IF; RETURN NEW; END; $$");jdbc.execute("CREATE TRIGGER step8_reject_audit BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION step8_reject_audit()");try{assertThrows(RuntimeException.class,()->configurations.put(admin,tenant,"https://other.example","other","new-secret",false,0L,Set.of()));assertEquals(before,jdbc.queryForMap("SELECT * FROM tenant_oidc_config WHERE tenant_id=?",tenant));assertEquals(groups,jdbc.queryForList("SELECT group_id FROM tenant_oidc_allowed_group WHERE tenant_id=? ORDER BY group_id",UUID.class,tenant));assertThrows(RuntimeException.class,()->configurations.put(admin,other,"https://fixture.example","client","secret",true,null,Set.of()));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM tenant_oidc_config WHERE tenant_id=?",Integer.class,other));assertEquals(count,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));}finally{jdbc.execute("DROP TRIGGER step8_reject_audit ON audit_log");jdbc.execute("DROP FUNCTION step8_reject_audit()");}}
 @Test void concurrentFirstConfigurationHasOneWinner()throws Exception{var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var tasks=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->{start.await();try{configure();return true;}catch(AuthFailure ex){assertEquals(409,ex.status());return false;}}));start.countDown();int winners=0;for(var task:tasks)if(task.get(10,TimeUnit.SECONDS))winners++;assertEquals(1,winners);assertEquals(0,configurations.get(admin,tenant).version());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='OIDC_CONFIG_CHANGED' AND target_id=?",Integer.class,tenant));}}
 @Test void physicalDeleteRemovesSingletonWithoutReset()throws Exception{configure();assertEquals(200,status(request(principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE),get("/api/v1/tenants/"+tenant+"/oidc"),null)));assertEquals(403,status(request(principal(Scope.GLOBAL,Role.USER,Status.ACTIVE),put("/api/v1/tenants/"+tenant+"/oidc"),input())));assertEquals(200,status(request(admin,delete("/api/v1/tenants/"+tenant+"/oidc").param("version","0"),null)));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM tenant_oidc_config WHERE tenant_id=?",Integer.class,tenant));}

 @Autowired OidcProvisioning provisioning;@Autowired org.springframework.transaction.PlatformTransactionManager tm;
 <T>T tx(java.util.function.Supplier<T> work){return new org.springframework.transaction.support.TransactionTemplate(tm).execute(t->work.get());}
 void awaitLock(String app)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<deadline){if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')",Boolean.class,app)))return;Thread.sleep(20);}fail("Expected actual PostgreSQL lock wait");}
 @Test void deleteIsPhysicalAuditAtomicAndRepeatedMissing()throws Exception{
  configure();var who=new Identity(UUID.randomUUID(),Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE,"Tenant administrator");
  assertEquals(409,status(request(who,delete("/api/v1/tenants/"+tenant+"/oidc").param("version","1"),null)));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM tenant_oidc_config WHERE tenant_id=?",Integer.class,tenant));
  assertEquals(200,status(request(who,delete("/api/v1/tenants/"+tenant+"/oidc").param("version","0"),null)));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM tenant_oidc_config WHERE tenant_id=?",Integer.class,tenant));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM tenant_oidc_allowed_group WHERE tenant_id=?",Integer.class,tenant));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM tenant WHERE id=?",Integer.class,tenant));
  var audit=jdbc.queryForMap("SELECT * FROM audit_log WHERE action='OIDC_CONFIG_DELETED' AND target_id=?",tenant);assertEquals(tenant,audit.get("tenant_id"));assertEquals(who.id(),audit.get("actor_id"));assertEquals(who.displayName(),audit.get("actor_display_name"));String data=audit.get("data").toString();for(String key:List.of("isolated-secret","encrypted","issuer","claims"))assertFalse(data.contains(key));
  assertEquals(404,status(request(who,get("/api/v1/tenants/"+tenant+"/oidc"),null)));assertEquals(404,status(request(who,delete("/api/v1/tenants/"+tenant+"/oidc").param("version","0"),null)));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='OIDC_CONFIG_DELETED' AND target_id=?",Integer.class,tenant));assertThrows(AuthFailure.class,()->configurations.provider(tenant));assertFalse(configurations.organizations(0,100,"").items().stream().anyMatch(o->o.routingId().equals(tenant)));
  assertEquals(409,assertThrows(AuthFailure.class,()->configurations.put(who,tenant,"https://fixture.example","client",null,true,0L,Set.of())).status());assertEquals(400,assertThrows(AuthFailure.class,()->configurations.put(who,tenant,"https://fixture.example","client",null,true,null,Set.of())).status());
  var created=configurations.put(who,tenant,"https://fixture.example","client","fresh-secret",true,null,Set.of());assertEquals(0,created.version());assertEquals(who.id(),created.metadata().createdBy());assertEquals("fresh-secret",configurations.secret(configurations.provider(tenant)));
 }
 @ParameterizedTest @CsvSource({"GLOBAL,ADMIN,200","TENANT,ADMIN,200","GLOBAL,USER,403","TENANT,USER,403"}) void httpCrudAuthority(Scope scope,Role role,int expected)throws Exception{var who=new Identity(UUID.randomUUID(),scope,scope==Scope.TENANT?tenant:null,role,Status.ACTIVE,"Actor");assertEquals(expected,status(request(who,put("/api/v1/tenants/"+tenant+"/oidc"),input())));if(expected==200){assertEquals(200,status(request(who,get("/api/v1/tenants/"+tenant+"/oidc"),null)));assertEquals(200,status(request(who,delete("/api/v1/tenants/"+tenant+"/oidc").param("version","0"),null)));}else{configure();assertEquals(403,status(request(who,get("/api/v1/tenants/"+tenant+"/oidc"),null)));assertEquals(403,status(request(who,delete("/api/v1/tenants/"+tenant+"/oidc").param("version","0"),null)));}}
 @Test void foreignUuidCannotWidenTenantAuthority()throws Exception{configure();configurations.put(admin,other,"https://foreign.example","client","secret",true,null,Set.of());var who=principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE);var before=jdbc.queryForMap("SELECT * FROM tenant_oidc_config WHERE tenant_id=?",other);assertEquals(404,status(request(who,get("/api/v1/tenants/"+other+"/oidc").param("tenantId",tenant.toString()),null)));assertEquals(404,status(request(who,put("/api/v1/tenants/"+other+"/oidc"),input())));assertEquals(404,status(request(who,delete("/api/v1/tenants/"+other+"/oidc").param("version","0"),null)));assertEquals(before,jdbc.queryForMap("SELECT * FROM tenant_oidc_config WHERE tenant_id=?",other));}
 @Test void disabledStillExistsAndCanBeDeleted(){var p=configure();configurations.put(admin,tenant,p.issuer(),p.clientId(),null,false,0L,p.allowedGroupIds());assertFalse(configurations.get(admin,tenant).enabled());assertThrows(AuthFailure.class,()->configurations.provider(tenant));assertFalse(configurations.organizations(0,100,"").items().stream().anyMatch(o->o.routingId().equals(tenant)));configurations.delete(admin,tenant,1);assertEquals(404,assertThrows(AuthFailure.class,()->configurations.get(admin,tenant)).status());}
 @Test void failedDeletionAuditRestoresSecretConfigAndGroups(){configure();var before=jdbc.queryForMap("SELECT * FROM tenant_oidc_config WHERE tenant_id=?",tenant);var groups=jdbc.queryForList("SELECT group_id FROM tenant_oidc_allowed_group WHERE tenant_id=? ORDER BY group_id",UUID.class,tenant);jdbc.execute("CREATE FUNCTION delete_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='OIDC_CONFIG_DELETED' THEN RAISE EXCEPTION 'isolated audit failure'; END IF; RETURN NEW; END; $$");jdbc.execute("CREATE TRIGGER delete_reject_audit BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION delete_reject_audit()");try{assertThrows(RuntimeException.class,()->configurations.delete(admin,tenant,0));assertEquals(before,jdbc.queryForMap("SELECT * FROM tenant_oidc_config WHERE tenant_id=?",tenant));assertEquals(groups,jdbc.queryForList("SELECT group_id FROM tenant_oidc_allowed_group WHERE tenant_id=? ORDER BY group_id",UUID.class,tenant));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='OIDC_CONFIG_DELETED' AND target_id=?",Integer.class,tenant));}finally{jdbc.execute("DROP TRIGGER delete_reject_audit ON audit_log");jdbc.execute("DROP FUNCTION delete_reject_audit()");}}
 @ParameterizedTest @ValueSource(strings={"","bad","-1","9223372036854775808"}) void deletionVersionValidation(String version)throws Exception{configure();var b=delete("/api/v1/tenants/"+tenant+"/oidc");if(!version.isEmpty())b.param("version",version);assertEquals(400,status(request(admin,b,null)));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM tenant_oidc_config WHERE tenant_id=?",Integer.class,tenant));}
 @ParameterizedTest @ValueSource(booleans={true,false}) void deleteAndFinalProvisioningHaveOrderedAtomicOutcomes(boolean deleteFirst)throws Exception{
  UUID group=UUID.randomUUID();configurations.put(admin,tenant,"https://fixture.example","client","secret",true,null,Set.of(group));var held=new CountDownLatch(1);var release=new CountDownLatch(1);String app="delete-jit-"+UUID.randomUUID();
  try(var pool=Executors.newFixedThreadPool(2)){
   var first=pool.submit(()->tx(()->{if(deleteFirst)configurations.delete(admin,tenant,0);else provisioning.complete(tenant,0,"https://fixture.example","client","race-subject","Person",Set.of(group));held.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return null;}));assertTrue(held.await(10,TimeUnit.SECONDS));
   var second=pool.submit(()->tx(()->{jdbc.queryForObject("SELECT set_config('application_name',?,true)",String.class,app);if(deleteFirst)return provisioning.complete(tenant,0,"https://fixture.example","client","race-subject","Person",Set.of(group));configurations.delete(admin,tenant,0);return null;}));
   try{awaitLock(app);}finally{release.countDown();}first.get(15,TimeUnit.SECONDS);if(deleteFirst){var error=assertThrows(ExecutionException.class,()->second.get(15,TimeUnit.SECONDS));assertInstanceOf(AuthFailure.class,error.getCause());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM external_identity WHERE issuer='https://fixture.example' AND subject='race-subject'",Integer.class));}else second.get(15,TimeUnit.SECONDS);
   assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM tenant_oidc_config WHERE tenant_id=?",Integer.class,tenant));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='OIDC_CONFIG_DELETED' AND target_id=?",Integer.class,tenant));
  }finally{release.countDown();}
 }
 @Test void finalProvisioningValidatesCurrentClientAndRecreationIsAllowed(){UUID group=UUID.randomUUID();configurations.put(admin,tenant,"https://fixture.example","old-client","secret",true,null,Set.of(group));configurations.delete(admin,tenant,0);configurations.put(admin,tenant,"https://fixture.example","new-client","fresh",true,null,Set.of(group));assertEquals(409,assertThrows(AuthFailure.class,()->provisioning.complete(tenant,0,"https://fixture.example","old-client","subject","Person",Set.of(group))).status());var pair=provisioning.complete(tenant,0,"https://fixture.example","new-client","subject","Person",Set.of(group));configurations.delete(admin,tenant,0);assertEquals(pair.user().id(),tokens.verify(pair.accessToken(),"access").userId());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM external_identity WHERE user_id=?",Integer.class,pair.user().id()));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id=?",Integer.class,pair.user().id()));}

 @Test void concurrentDeletesCreateExactlyOneAudit()throws Exception{configure();var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var tasks=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->{start.await();try{configurations.delete(admin,tenant,0);return true;}catch(AuthFailure e){assertEquals(404,e.status());return false;}}));start.countDown();int success=0;for(var t:tasks)if(t.get(10,TimeUnit.SECONDS))success++;assertEquals(1,success);assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='OIDC_CONFIG_DELETED' AND target_id=?",Integer.class,tenant));}}
 @ParameterizedTest @ValueSource(booleans={true,false}) void updateDeleteRaceRetainsCurrentVersionGuard(boolean deleteFirst)throws Exception{configure();var held=new CountDownLatch(1);var release=new CountDownLatch(1);String app="update-delete-"+UUID.randomUUID();try(var pool=Executors.newFixedThreadPool(2)){var first=pool.submit(()->tx(()->{if(deleteFirst)configurations.delete(admin,tenant,0);else configurations.put(admin,tenant,"https://fixture.example","client",null,true,0L,Set.of());held.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return null;}));assertTrue(held.await(10,TimeUnit.SECONDS));var second=pool.submit(()->tx(()->{jdbc.queryForObject("SELECT set_config('application_name',?,true)",String.class,app);if(deleteFirst)return configurations.put(admin,tenant,"https://fixture.example","client","fresh",true,0L,Set.of());configurations.delete(admin,tenant,0);return null;}));try{awaitLock(app);}finally{release.countDown();}first.get(15,TimeUnit.SECONDS);var e=assertThrows(ExecutionException.class,()->second.get(15,TimeUnit.SECONDS));assertInstanceOf(AuthFailure.class,e.getCause());assertEquals(409,((AuthFailure)e.getCause()).status());assertEquals(deleteFirst?0:1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='OIDC_CONFIG_CHANGED' AND target_id=? AND (data->>'version')='1'",Integer.class,tenant));assertEquals(deleteFirst?1:0,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='OIDC_CONFIG_DELETED' AND target_id=?",Integer.class,tenant));}finally{release.countDown();}}
 @Test void recreateWaitsForDeleteAndCreatesOnlyCurrentSingleton()throws Exception{configure();var held=new CountDownLatch(1);var release=new CountDownLatch(1);String app="delete-recreate-"+UUID.randomUUID();try(var pool=Executors.newFixedThreadPool(2)){var first=pool.submit(()->tx(()->{configurations.delete(admin,tenant,0);held.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return null;}));assertTrue(held.await(10,TimeUnit.SECONDS));var second=pool.submit(()->tx(()->{jdbc.queryForObject("SELECT set_config('application_name',?,true)",String.class,app);return configurations.put(admin,tenant,"https://fixture.example","client","fresh-recreated",true,null,Set.of());}));try{awaitLock(app);}finally{release.countDown();}first.get(15,TimeUnit.SECONDS);assertEquals(0,second.get(15,TimeUnit.SECONDS).version());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM tenant_oidc_config WHERE tenant_id=?",Integer.class,tenant));assertEquals("fresh-recreated",configurations.secret(configurations.provider(tenant)));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='OIDC_CONFIG_DELETED' AND target_id=?",Integer.class,tenant));}finally{release.countDown();}}
}
