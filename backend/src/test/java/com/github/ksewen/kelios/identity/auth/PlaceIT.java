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
class PlaceIT {
 static class MutableClock extends Clock {
  Instant now=Instant.parse("2026-10-11T12:00:00Z");
  public Instant instant(){return now;} public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
 }
 @org.springframework.boot.test.context.TestConfiguration static class Time {
  @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary MutableClock labelClock(){return new MutableClock();}
 }
 @Autowired MutableClock clock; @Autowired PlaceOperations operations;
 @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17-alpine");
 static final AuthTestKeys KEYS;static{try{KEYS=new AuthTestKeys("place-keys");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",PG::getJdbcUrl);p.add("spring.datasource.username",PG::getUsername);p.add("spring.datasource.password",PG::getPassword);p.add("kelios.auth.key-directory",()->KEYS.directory.toAbsolutePath().toString());p.add("kelios.auth.public-origin",()->"https://kelios.example");p.add("kelios.auth.issuer",()->"https://kelios.example");}
 @Autowired AssetService assets;@Autowired CatalogService catalog;@Autowired PlaceService places;@Autowired CatalogMergeService merges;
 @Autowired TenantService tenants;@Autowired UserService users;@Autowired IdentityStore identities;@Autowired AuthenticationService auth;
 @Autowired OidcConfigurationService configs;@Autowired OidcProvisioning provisioning;@Autowired JwtTokens tokens;
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

 String plural(PlaceService.Kind kind){return kind==PlaceService.Kind.LOCATION?"locations":"owners";}
 String root(PlaceService.Kind kind){return "/api/v1/"+plural(kind);}
 String contextual(PlaceService.Kind kind,UUID t){return "/api/v1/tenants/"+t+"/"+plural(kind);}
 UUID create(PlaceService.Kind kind,UUID t,String name,String note){return places.create(kind,t,name,note,actor);}
 String prefix(){return "place-"+UUID.randomUUID()+"-";}
 Map<String,Object> command(String name,Object note){var b=new LinkedHashMap<String,Object>();b.put("name",name);b.put("note",note);return b;}
 @SuppressWarnings("unchecked") List<Map<String,Object>> items(MvcResult r){return (List<Map<String,Object>>)data(r).get("items");}
 long total(MvcResult r){return ((Number)data(r).get("totalElements")).longValue();}
 MvcResult list(PlaceService.Kind kind,UUID t,Map<String,String> query)throws Exception{var b=get(contextual(kind,t));query.forEach(b::param);var r=request(admin,b,null);assertEquals(200,status(r),r.getResponse().getContentAsString());return r;}
 @ParameterizedTest @EnumSource(PlaceService.Kind.class) void defaultsAndSizes(PlaceService.Kind kind)throws Exception{
  String p=prefix();UUID first=create(kind,tenant,p+"A",null);clock.now=clock.now.plusSeconds(1);UUID latest=create(kind,tenant,p+"Z",null);
  var r=list(kind,tenant,Map.of("q",p));assertEquals(0,data(r).get("page"));assertEquals(20,data(r).get("size"));assertEquals(latest.toString(),items(r).getFirst().get("id"));assertEquals(first.toString(),items(r).getLast().get("id"));
  for(int i=0;i<103;i++)create(kind,tenant,p+i,null);
  for(int size:List.of(1,10,20,30,40,50,100)){r=list(kind,tenant,Map.of("q",p,"size",String.valueOf(size)));assertEquals(size,items(r).size());assertEquals(size,data(r).get("size"));assertEquals(105,total(r));}
  r=list(kind,tenant,Map.of("q",p,"page","2147483647","size","100"));assertTrue(items(r).isEmpty());assertEquals(105,total(r));assertEquals(2147483647,data(r).get("page"));
  assertEquals(0,total(list(kind,tenant,Map.of("q",prefix()))));assertEquals(25,ListInput.size(Map.of()),"Unmigrated defaults stay separately gated");
 }
 static java.util.stream.Stream<Arguments> invalidQueries(){return java.util.stream.Stream.of(
  new String[]{"page","-1"},new String[]{"page","2147483648"},new String[]{"page","1.5"},new String[]{"size","0"},new String[]{"size","101"},new String[]{"size","bad"},new String[]{"sort","createdAt"},new String[]{"sort","note"},new String[]{"sort","id"},new String[]{"sort","name;DROP TABLE owner"},new String[]{"direction","up"},new String[]{"direction","desc"},new String[]{"note","text"},new String[]{"visibility","GLOBAL"},new String[]{"deleted","false"},new String[]{"deleted","true"},new String[]{"deleted","all"},new String[]{"unpaged","true"},new String[]{"tenantId",UUID.randomUUID().toString()})
  .flatMap(v->java.util.Arrays.stream(PlaceService.Kind.values()).map(k->Arguments.of(k,v[0],v[1])));}
 @ParameterizedTest @MethodSource("invalidQueries") void strictQuery(PlaceService.Kind kind,String key,String value)throws Exception{var r=request(admin,get(contextual(kind,tenant)).param(key,value),null);assertEquals(400,status(r));assertEquals("INVALID_INPUT",data(r).get("code"));assertTrue(r.getResponse().getContentType().startsWith("application/problem+json"));}
 @ParameterizedTest @EnumSource(PlaceService.Kind.class) void literalNameSearchNeverSearchesNote(PlaceService.Kind kind)throws Exception{
  String p=prefix();create(kind,tenant,p+"%_\\Case","Unsearchable-note-"+p);create(kind,tenant,p+"XXCase",null);create(kind,other,p+"%_\\Case",null);
  assertEquals(1,total(list(kind,tenant,Map.of("q","  "+p.toUpperCase()+"%_\\  "))));assertEquals(0,total(list(kind,tenant,Map.of("q","Unsearchable-note-"+p))));
  assertEquals(total(list(kind,tenant,Map.of())),total(list(kind,tenant,Map.of("q"," ".repeat(200)))));
  create(kind,tenant,"x".repeat(128),null);assertTrue(total(list(kind,tenant,Map.of("q"," "+"x".repeat(128)+" ")))>0);
  for(String q:List.of("x".repeat(129),"😀".repeat(65)))assertEquals(400,status(request(admin,get(contextual(kind,tenant)).param("q",q),null)));
 }
 @ParameterizedTest @EnumSource(PlaceService.Kind.class) void scopedFullSortAndPaging(PlaceService.Kind kind)throws Exception{
  String p=prefix();var expected=new HashSet<String>();for(int i=0;i<67;i++)expected.add(create(kind,tenant,p+(char)('A'+i%3),null).toString());
  create(kind,other,p+"Foreign",null);UUID gone=create(kind,tenant,p+"Deleted",null);places.delete(kind,tenant,gone,actor);
  for(String direction:List.of("asc","desc")){
   var seen=new HashSet<String>();String previous=null;
   for(int page=0;page<4;page++){
    var r=list(kind,tenant,Map.of("q",p,"sort","name","direction",direction,"page",String.valueOf(page),"size","20"));assertEquals(67,total(r));
    for(var row:items(r)){assertTrue(seen.add((String)row.get("id")));String current=(String)row.get("name");if(previous!=null)assertTrue(direction.equals("asc")?previous.compareTo(current)<=0:previous.compareTo(current)>=0);previous=current;}
   }
   assertEquals(expected,seen);
  }
  assertEquals(items(list(kind,tenant,Map.of("q",p,"sort","name","direction","asc"))),items(list(kind,tenant,Map.of("q",p,"sort","name"))));
 }
 @ParameterizedTest @EnumSource(PlaceService.Kind.class) void transportTenantNeverDeterminesTenantAuthority(PlaceService.Kind kind)throws Exception{
  String p=prefix();UUID own=create(kind,tenant,p+"Own",null),foreign=create(kind,other,p+"Foreign",null);var local=principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE);
  for(UUID supplied:List.of(tenant,other,UUID.randomUUID())){
   var r=request(local,get(contextual(kind,supplied)).param("q",p),null);assertEquals(200,status(r));assertEquals(1,total(r));assertEquals(own.toString(),items(r).getFirst().get("id"));
   r=request(local,post(contextual(kind,supplied)),command("Created outside query","note"));assertEquals(201,status(r));UUID id=UUID.fromString((String)data(r).get("id"));assertEquals(tenant,places.read(kind,id).tenantId());
  }
  for(UUID id:List.of(foreign,UUID.randomUUID())){
   assertEquals(404,status(request(local,get(root(kind)+"/"+id),null)));assertEquals(404,status(request(local,put(root(kind)+"/"+id),command("Attack",null))));assertEquals(404,status(request(local,delete(root(kind)+"/"+id),null)));
  }
  assertEquals(200,status(request(admin,get(root(kind)+"/"+foreign),null)));assertEquals(404,status(request(admin,get(contextual(kind,UUID.randomUUID())),null)));
 }
 @ParameterizedTest @EnumSource(PlaceService.Kind.class) void detailProjectionAndNoteReplacement(PlaceService.Kind kind)throws Exception{
  var r=request(admin,post(contextual(kind,tenant)),command(" Initial "," Note "));assertEquals(201,status(r));UUID id=UUID.fromString((String)data(r).get("id"));assertEquals(root(kind)+"/"+id,r.getResponse().getHeader("Location"));
  long audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);var before=data(request(admin,get(root(kind)+"/"+id),null));assertEquals("Initial",before.get("name"));assertEquals("Note",before.get("note"));assertEquals(tenant.toString(),((Map<?,?>)before.get("tenant")).get("id"));assertTrue(((Map<?,?>)before.get("tenant")).get("name").toString().startsWith("Tenant"));assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  var row=items(list(kind,tenant,Map.of("q","Initial"))).getFirst();assertFalse(row.containsKey("note"));assertFalse(row.containsKey("deletedAt"));assertFalse(before.containsKey("passwordHash"));
  var initial=(Map<?,?>)before.get("metadata");assertEquals(initial.get("createdBy"),initial.get("updatedBy"));assertEquals(initial.get("createdAt"),initial.get("updatedAt"));
  var editor=principal(Scope.GLOBAL,Role.ADMIN,Status.ACTIVE);clock.now=clock.now.plusSeconds(5);
  assertEquals(204,status(request(editor,put(root(kind)+"/"+id),command("Initial","Edited note"))));var after=data(request(admin,get(root(kind)+"/"+id),null));var metadata=(Map<?,?>)after.get("metadata");assertEquals(initial.get("createdBy"),metadata.get("createdBy"));assertEquals(initial.get("createdAt"),metadata.get("createdAt"));assertEquals(editor.id().toString(),metadata.get("updatedBy"));assertNotEquals(initial.get("updatedAt"),metadata.get("updatedAt"));assertEquals("Edited note",after.get("note"));
  for(Object empty:java.util.Arrays.asList(null,"   ")){assertEquals(204,status(request(admin,put(root(kind)+"/"+id),command("Initial",empty))));assertNull(data(request(admin,get(root(kind)+"/"+id),null)).get("note"));}
  assertEquals(204,status(request(admin,put(root(kind)+"/"+id),Map.of("name","Initial"))));assertNull(data(request(admin,get(root(kind)+"/"+id),null)).get("note"));
  assertEquals(204,status(request(admin,delete(root(kind)+"/"+id),null)));assertEquals(true,data(request(admin,get(root(kind)+"/"+id),null)).get("deleted"));assertEquals(0,total(list(kind,tenant,Map.of("q","Initial"))));assertEquals(409,status(request(admin,put(root(kind)+"/"+id),command("Deleted",null))));
 }
 @ParameterizedTest @EnumSource(PlaceService.Kind.class) void strictCommandsNoOverposting(PlaceService.Kind kind)throws Exception{
  UUID id=create(kind,tenant,"Before",null);
  for(String field:List.of("createdBy","createdAt","updatedBy","updatedAt","tenantId","tenant","deletedAt","deletedBy","actor","visibility")){
   var b=command("Overposting",null);b.put(field,"forged");assertEquals(400,status(request(admin,post(contextual(kind,tenant)),b)));assertEquals(400,status(request(admin,put(root(kind)+"/"+id),b)));
  }
  for(Object bad:List.of(1,true,List.of("text"))){assertEquals(400,status(request(admin,post(contextual(kind,tenant)),command("Name",bad))));var b=command("Name",null);b.put("name",bad);assertEquals(400,status(request(admin,post(contextual(kind,tenant)),b)));}
  assertEquals(400,status(request(admin,post(contextual(kind,tenant)),command("  ",null))));assertEquals(400,status(request(admin,put(root(kind)+"/"+id),Map.of("note","Note only without replacement name"))));
  assertEquals(400,status(request(admin,delete(root(kind)+"/"+id),Map.of("deletedBy",actor.id().toString()))));
 }
 static java.util.stream.Stream<Arguments> retainedCases(){return java.util.Arrays.stream(PlaceService.Kind.values()).flatMap(k->java.util.stream.Stream.of("registered","deregistered","deleted").map(state->Arguments.of(k,state)));}
 @ParameterizedTest @MethodSource("retainedCases") void distinctGuardsForAllRetainedAssetStates(PlaceService.Kind kind,String state)throws Exception{
  var a=asset();if(!state.equals("registered"))assets.lifecycle(tenant,a.id(),0,LifecycleState.DEREGISTERED,DeregistrationReason.ERROR,null,actor);if(state.equals("deleted"))assets.delete(tenant,a.id(),1,actor);
  UUID id=kind==PlaceService.Kind.LOCATION?location:owner;var before=places.read(kind,id);long history=events(a.id());var assetBefore=assets.get(tenant,a.id());long audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
  var noteEdit=request(admin,put(root(kind)+"/"+id),command(before.name(),"Note only changed"));var nameEdit=request(admin,put(root(kind)+"/"+id),command("Renamed",before.note()));
  if(kind==PlaceService.Kind.LOCATION){assertEquals(204,status(noteEdit));assertEquals(204,status(nameEdit));assertEquals(id,places.read(kind,id).id());assertEquals("Renamed",places.read(kind,id).name());assertEquals(audits+2,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));}
  else{assertEquals(409,status(noteEdit));assertEquals("REFERENCED",data(noteEdit).get("code"));assertEquals(409,status(nameEdit));assertEquals(before,places.read(kind,id));assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));}
  assertEquals(409,status(request(admin,delete(root(kind)+"/"+id),null)));assertEquals(history,events(a.id()));assertEquals(assetBefore,assets.get(tenant,a.id()));
 }
 @Test void explicitlyResolvingReferencesAllowsOwnerEditAndDeletion()throws Exception{
  var a=asset();UUID alternative=places.create(PlaceService.Kind.LOCATION,tenant,"Alternative",actor);assets.changeLocation(tenant,a.id(),0,alternative,actor);assets.changeOwner(tenant,a.id(),1,null,actor);
  assertEquals(204,status(request(admin,put(root(PlaceService.Kind.OWNER)+"/"+owner),command("Owner","Allowed note"))));assertEquals("Allowed note",data(request(admin,get(root(PlaceService.Kind.OWNER)+"/"+owner),null)).get("note"));
  assertEquals(204,status(request(admin,delete(root(PlaceService.Kind.OWNER)+"/"+owner),null)));assertEquals(204,status(request(admin,delete(root(PlaceService.Kind.LOCATION)+"/"+location),null)));assertEquals(3,events(a.id()));
 }
 @ParameterizedTest @EnumSource(PlaceService.Kind.class) void roleStatusAndNoMerge(PlaceService.Kind kind)throws Exception{
  UUID id=create(kind,tenant,"Own",null);
  for(Scope scope:Scope.values())for(Role role:Role.values()){
   var who=principal(scope,role,Status.ACTIVE);assertEquals(200,status(request(who,get(root(kind)+"/"+id),null)));assertEquals(200,status(request(who,get(contextual(kind,tenant)),null)));
   assertEquals(role==Role.ADMIN?201:403,status(request(who,post(contextual(kind,tenant)),command("Create",null))));assertEquals(role==Role.ADMIN?204:403,status(request(who,put(root(kind)+"/"+id),command("Edit",null))));
   var pending=principal(scope,role,Status.PENDING);assertEquals(403,status(request(pending,get(contextual(kind,tenant)),null)));assertEquals(403,status(request(pending,get(root(kind)+"/"+id),null)));assertEquals(403,status(request(pending,post(contextual(kind,tenant)),command("Blocked",null))));
  }
  var disabled=principal(Scope.GLOBAL,Role.ADMIN,Status.DISABLED);assertThrows(AuthFailure.class,()->tokens.login(disabled));
  org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(disabled,null,List.of()));
  try{assertThrows(AuthFailure.class,()->operations.detail(kind,id));assertThrows(AuthFailure.class,()->operations.create(kind,tenant,new PlaceModel.Command("Blocked",null)));}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
  long audit=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);for(String suffix:List.of("merge","merge-preview"))assertEquals(404,status(request(admin,post(root(kind)+"/"+suffix),Map.of())));assertEquals(audit,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
 }
 static java.util.stream.Stream<Arguments> rollbackCases(){return java.util.Arrays.stream(PlaceService.Kind.values()).flatMap(k->java.util.stream.Stream.of("create","edit","delete").map(action->Arguments.of(k,action)));}
 @ParameterizedTest @MethodSource("rollbackCases") void mutationAuditFailureRollback(PlaceService.Kind kind,String operation)throws Exception{
  UUID id=create(kind,tenant,"Before","Before note");var before=data(request(admin,get(root(kind)+"/"+id),null));String table=kind.name().toLowerCase();long count=jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class),audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
  String action=kind+switch(operation){case "create"->"_CREATED";case "edit"->"_EDITED";default->"_DELETED";};
  jdbc.execute("CREATE FUNCTION step4_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='"+action+"' THEN RAISE EXCEPTION 'isolated failure' USING ERRCODE='23514'; END IF; RETURN NEW; END; $$");jdbc.execute("CREATE TRIGGER step4_reject_audit BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION step4_reject_audit()");clock.now=clock.now.plusSeconds(10);
  try{
   var r=switch(operation){case "create"->request(admin,post(contextual(kind,tenant)),command("Rollback","New note"));case "edit"->request(admin,put(root(kind)+"/"+id),command("Rollback","New note"));default->request(admin,delete(root(kind)+"/"+id),null);};
   assertEquals(409,status(r));assertEquals(before,data(request(admin,get(root(kind)+"/"+id),null)));assertEquals(count,jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class));assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  }finally{jdbc.execute("DROP TRIGGER step4_reject_audit ON audit_log");jdbc.execute("DROP FUNCTION step4_reject_audit()");}
 }
}
