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
class AuditQueryIT {
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
 static final AuthTestKeys KEYS;static{try{KEYS=new AuthTestKeys("audit-query-keys");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
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





 UUID record(UUID scope,UUID actorId,String label,String action,String type,UUID target,Instant at){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO audit_log(id,tenant_id,actor_id,actor_display_name,action,target_type,target_id,occurred_at,data) VALUES(?,?,?,?,?,?,?,?, '{\"snapshot\":null}'::jsonb)",id,scope,actorId,label,action,type,target,java.sql.Timestamp.from(at));return id;}
 Map<String,Object> page(Identity who,Map<String,String> query)throws Exception{var b=get("/api/v1/audit-log");query.forEach(b::param);var r=request(who,b,null);assertEquals(200,status(r));return data(r);}
 List<Map<String,Object>> items(Map<String,Object> p){return (List<Map<String,Object>>)p.get("items");}
 String unique(){return "query-"+UUID.randomUUID();}
 @Test void combinedScopeActorTargetTimeAndPagingIsServerAuthoritative()throws Exception{
  String action=unique();UUID target=UUID.randomUUID(),actorId=UUID.randomUUID();Instant base=Instant.parse("2026-10-12T10:00:00Z");var ids=new ArrayList<UUID>();
  for(int i=0;i<67;i++)ids.add(record(tenant,actorId,"Historical label",action,"ASSET",target,base.plusSeconds(i)));record(other,actorId,"Foreign",action,"ASSET",target,base.plusSeconds(30));record(null,actorId,"Global",action,"ASSET",target,base.plusSeconds(30));record(tenant,UUID.randomUUID(),"Different actor",action,"ASSET",target,base.plusSeconds(30));record(tenant,actorId,"Different target",action,"ASSET",UUID.randomUUID(),base.plusSeconds(30));
  var q=new HashMap<>(Map.of("action",action,"targetType","ASSET","targetId",target.toString(),"actorId",actorId.toString(),"tenantId",tenant.toString(),"from",base.plusSeconds(1).toString(),"until",base.plusSeconds(65).toString(),"sort","occurredAt","direction","asc","size","20"));
  var found=new ArrayList<String>();for(int page=0;page<4;page++){q.put("page",Integer.toString(page));var p=page(admin,q);assertEquals(65,((Number)p.get("totalElements")).intValue());for(var item:items(p))found.add((String)item.get("id"));}assertEquals(ids.subList(1,66).stream().map(UUID::toString).toList(),found);
  q.put("direction","desc");q.put("page","0");var p=page(admin,q);assertEquals(ids.get(65).toString(),items(p).getFirst().get("id"));q.put("page","100");p=page(admin,q);assertTrue(items(p).isEmpty());assertEquals(65,((Number)p.get("totalElements")).intValue());assertEquals(100,((Number)p.get("page")).intValue());
 }
 @ParameterizedTest @ValueSource(strings={"none","from","until","equal","offset"}) void inclusiveOptionalMicrosecondUtcBounds(String mode)throws Exception{String a=unique();UUID actorId=UUID.randomUUID(),target=UUID.randomUUID();Instant at=Instant.parse("2026-10-12T10:00:00.123456Z");record(tenant,actorId,"Captured",a,"USER",target,at.minusNanos(1000));UUID middle=record(tenant,actorId,"Captured",a,"USER",target,at);record(tenant,actorId,"Captured",a,"USER",target,at.plusNanos(1000));var q=new HashMap<>(Map.of("action",a));switch(mode){case "from"->q.put("from",at.toString());case "until"->q.put("until",at.toString());case "equal"->{q.put("from",at.toString());q.put("until",at.toString());}case "offset"->{q.put("from","2026-10-12T12:00:00.123456+02:00");q.put("until","2026-10-12T12:00:00.123456+02:00");}}var p=page(admin,q);assertEquals(mode.equals("none")?3:mode.equals("from")||mode.equals("until")?2:1,((Number)p.get("totalElements")).intValue());if(items(p).size()==1)assertEquals(middle.toString(),items(p).getFirst().get("id"));}
 @ParameterizedTest @ValueSource(ints={1,10,20,30,40,50,100}) void pagingDefaultsAndSupportedSizes(int size)throws Exception{var p=page(admin,Map.of("size",Integer.toString(size)));assertEquals(size,((Number)p.get("size")).intValue());assertTrue(items(p).size()<=size);p=page(admin,Map.of());assertEquals(0,((Number)p.get("page")).intValue());assertEquals(20,((Number)p.get("size")).intValue());}
 @ParameterizedTest @MethodSource("invalidQueries") void strictQueryValidation(String key,String value)throws Exception{assertEquals(400,status(request(admin,get("/api/v1/audit-log").param(key,value),null)));}
 static java.util.stream.Stream<Arguments> invalidQueries(){return java.util.stream.Stream.of(new String[]{"page","-1"},new String[]{"page","2147483648"},new String[]{"size","0"},new String[]{"size","101"},new String[]{"size","bad"},new String[]{"q","anything"},new String[]{"sort","actorDisplayName"},new String[]{"sort","occurredAt;DROP TABLE audit_log"},new String[]{"direction","up"},new String[]{"actorId","bad"},new String[]{"targetId","bad"},new String[]{"tenantId","bad"},new String[]{"globalOnly","yes"},new String[]{"from","2026-10-12"},new String[]{"until","2026-10-12T10:00:00"},new String[]{"from","2026-10-12T10:00:00.1234567Z"},new String[]{"until","bad"},new String[]{"username","someone"},new String[]{"deleted","all"},new String[]{"unpaged","true"}).map(v->Arguments.of(v[0],v[1]));}
 @Test void reversedAndIncompatibleFiltersRejected()throws Exception{assertEquals(400,status(request(admin,get("/api/v1/audit-log").param("from","2026-10-13T00:00:00Z").param("until","2026-10-12T00:00:00Z"),null)));assertEquals(400,status(request(admin,get("/api/v1/audit-log").param("tenantId",tenant.toString()).param("globalOnly","false"),null)));}
 @Test void tenantScopeCountDetailAndHistoricalActorDoNotDependOnLiveUsers()throws Exception{String action=unique();UUID absentActor=UUID.randomUUID(),target=UUID.randomUUID();Instant at=Instant.parse("2026-10-12T10:00:00Z");UUID own=record(tenant,absentActor,"Historical display",action,"USER",target,at);UUID foreign=record(other,absentActor,"Historical display",action,"USER",target,at);record(null,absentActor,"Historical display",action,"USER",target,at);var who=new Identity(UUID.randomUUID(),Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE,"Admin");var p=page(who,Map.of("action",action,"actorId",absentActor.toString()));assertEquals(1,((Number)p.get("totalElements")).intValue());assertEquals(own.toString(),items(p).getFirst().get("id"));var detail=request(who,get("/api/v1/audit-log/"+own),null);assertEquals(200,status(detail));assertEquals("Historical display",data(detail).get("actorDisplayName"));assertEquals(Set.of("id","tenantId","actorId","actorDisplayName","action","targetType","targetId","occurredAt","data"),data(detail).keySet());var denied=request(who,get("/api/v1/audit-log/"+foreign),null);var missing=request(who,get("/api/v1/audit-log/"+UUID.randomUUID()),null);assertEquals(404,status(denied));assertEquals(404,status(missing));assertEquals(data(denied).get("detail"),data(missing).get("detail"));for(var q:List.of(Map.of("tenantId",tenant.toString()),Map.of("tenantId",other.toString()),Map.of("globalOnly","false")))assertEquals(403,status(request(who,get("/api/v1/audit-log").param(q.keySet().iterator().next(),q.values().iterator().next()),null)));assertEquals(1,((Number)page(admin,Map.of("action",action,"globalOnly","true")).get("totalElements")).intValue());assertEquals(3,((Number)page(admin,Map.of("action",action,"globalOnly","false")).get("totalElements")).intValue());}
 @ParameterizedTest @CsvSource({"GLOBAL,ADMIN,ACTIVE,200","TENANT,ADMIN,ACTIVE,200","GLOBAL,USER,ACTIVE,403","TENANT,USER,ACTIVE,403","GLOBAL,ADMIN,PENDING,403","TENANT,ADMIN,PENDING,403"}) void authorizationMatrix(Scope scope,Role role,Status state,int result)throws Exception{var who=new Identity(UUID.randomUUID(),scope,scope==Scope.TENANT?tenant:null,role,state,"Actor");assertEquals(result,status(request(who,get("/api/v1/audit-log"),null)));}
 @Test void exactMachineStringsAreLiteralAndQueriesDoNotMutateHistory()throws Exception{String action=unique()+"%_\\";UUID id=record(tenant,UUID.randomUUID(),"Old label",action,"ASSET",UUID.randomUUID(),Instant.parse("2026-10-12T10:00:00Z"));var before=jdbc.queryForList("SELECT to_jsonb(a)::text FROM audit_log a ORDER BY id",String.class);assertEquals(1,((Number)page(admin,Map.of("action",action)).get("totalElements")).intValue());assertEquals(0,((Number)page(admin,Map.of("action",action.substring(0,action.length()-3))).get("totalElements")).intValue());assertEquals(200,status(request(admin,get("/api/v1/audit-log/"+id),null)));assertEquals(before,jdbc.queryForList("SELECT to_jsonb(a)::text FROM audit_log a ORDER BY id",String.class));assertEquals(401,status(request(null,get("/api/v1/audit-log"),null)));}
 @Test void localDayUtcBoundsHandleDstAndLongRangesArePermitted()throws Exception{String action=unique();var zone=ZoneId.of("Europe/Berlin");var day=LocalDate.of(2026,3,29);Instant from=day.atStartOfDay(zone).toInstant(),until=day.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1000);assertEquals(23,Duration.between(from,until.plusNanos(1000)).toHours());record(tenant,actor.id(),"Actor",action,"USER",UUID.randomUUID(),from);record(tenant,actor.id(),"Actor",action,"USER",UUID.randomUUID(),until);record(tenant,actor.id(),"Actor",action,"USER",UUID.randomUUID(),until.plusNanos(1000));assertEquals(2,((Number)page(admin,Map.of("action",action,"from",from.toString(),"until",until.toString())).get("totalElements")).intValue());assertEquals(3,((Number)page(admin,Map.of("action",action,"from","1900-01-01T00:00:00Z","until","2100-01-01T00:00:00Z")).get("totalElements")).intValue());}
}
