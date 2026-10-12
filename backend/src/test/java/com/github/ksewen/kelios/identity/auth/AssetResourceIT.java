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
class AssetResourceIT {
 static class MutableClock extends Clock {
  Instant now=Instant.parse("2026-10-11T12:00:00Z");
  public Instant instant(){return now;} public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
 }
 @org.springframework.boot.test.context.TestConfiguration static class Time {
  @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary MutableClock labelClock(){return new MutableClock();}
 }
 @Autowired MutableClock clock; @Autowired InventoryOperations operations;
 @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17-alpine");
 static final AuthTestKeys KEYS;static{try{KEYS=new AuthTestKeys("asset-resource-keys");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
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


 String path(){return "/api/v1/tenants/"+tenant+"/assets";}
 @SuppressWarnings("unchecked") List<Map<String,Object>> items(MvcResult r){return (List<Map<String,Object>>)data(r).get("items");}
 long total(MvcResult r){return ((Number)data(r).get("totalElements")).longValue();}
 MvcResult list(Map<String,String> query)throws Exception{var b=get(path());query.forEach(b::param);var r=request(admin,b,null);assertEquals(200,status(r),r.getResponse().getContentAsString());return r;}
 AssetService.View create(String text,boolean loanable){return assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(product),location,owner,loanable,text,text,"not searchable",text),actor);}
 @Test void defaultsSizesScopeAndBeyondEnd()throws Exception{
  var ids=new HashSet<String>();for(int i=0;i<103;i++){clock.now=clock.now.plusSeconds(1);ids.add(create("queryfixture",true).id().toString());}
  var r=list(Map.of());assertEquals(20,data(r).get("size"));assertEquals(0,data(r).get("page"));assertEquals(clock.now.toString(),items(r).getFirst().get("createdAt"));
  for(int size:List.of(1,10,20,30,40,50,100)){r=list(Map.of("size",String.valueOf(size)));assertEquals(size,items(r).size());assertEquals(103,total(r));}
  r=list(Map.of("page","2147483647","size","100"));assertTrue(items(r).isEmpty());assertEquals(103,total(r));
  assertEquals(0,total(list(Map.of("q","missing text"))));assertEquals(25,ListInput.size(Map.of()));
 }
 static java.util.stream.Stream<Arguments> invalidQueries(){return java.util.stream.Stream.of(
  new String[]{"page","-1"},new String[]{"page","2147483648"},new String[]{"page","1.5"},new String[]{"size","0"},new String[]{"size","101"},new String[]{"size","bad"},
  new String[]{"sort","name"},new String[]{"sort","localIdentifier"},new String[]{"sort","ownerId"},new String[]{"sort","tag;DROP TABLE asset"},new String[]{"direction","up"},new String[]{"loanable","1"},new String[]{"loaned","yes"},new String[]{"lifecycle","bad"},new String[]{"condition","bad"},new String[]{"locationId","bad"},new String[]{"deleted","true"},new String[]{"deleted","all"},new String[]{"unpaged","true"},new String[]{"note","text"},new String[]{"tenantId",UUID.randomUUID().toString()}).map(v->Arguments.of(v[0],v[1]));}
 @ParameterizedTest @MethodSource("invalidQueries") void strictQuery(String key,String value)throws Exception{var r=request(admin,get(path()).param(key,value),null);assertEquals(400,status(r));assertEquals("INVALID_INPUT",data(r).get("code"));}
 @ParameterizedTest @ValueSource(strings={"q","serialNumber","localIdentifier","externalInventoryNumber"}) void literalNormalizedIdentifiers(String field)throws Exception{
  create("Literal%_\\Case",true);create("LiteralXXCase",false);
  assertEquals(1,total(list(Map.of(field,"  literal%_\\  "))));assertEquals(2,total(list(Map.of(field," ".repeat(200)))));
  assertEquals(400,status(request(admin,get(path()).param(field,"x".repeat(129)),null)));
  assertEquals(400,status(request(admin,get(path()).param(field,"😀".repeat(65)),null)));
  assertEquals(0,total(list(Map.of(field,"not searchable"))));
 }
 @Test void combinedFullResultFiltersAndPaging()throws Exception{
  for(int i=0;i<44;i++)create("wanted",i%2==0);create("different",true);
  var q=new LinkedHashMap<String,String>();
  q.put("q","wanted");q.put("serialNumber","wanted");q.put("localIdentifier","wanted");q.put("externalInventoryNumber","wanted");q.put("loanable","true");q.put("loaned","false");q.put("lifecycle","REGISTERED");q.put("condition","UNKNOWN");q.put("locationId",location.toString());q.put("ownerId",owner.toString());q.put("categoryId",category.toString());q.put("manufacturerId",manufacturer.toString());q.put("productId",product.toString());q.put("size","20");
  var r=list(q);assertEquals(22,total(r));assertEquals(20,items(r).size());q.put("page","1");r=list(q);assertEquals(2,items(r).size());assertEquals(22,total(r));
  q.put("loaned","true");assertEquals(0,total(list(q)));
 }
 @ParameterizedTest @ValueSource(strings={"tag","createdAt","lifecycle","condition"}) void serverSortingBeforePaging(String sort)throws Exception{
  var expected=new HashSet<String>();for(int i=0;i<65;i++){var a=create("sort fixture",true);expected.add(a.id().toString());jdbc.update("UPDATE asset SET lifecycle=?,condition=? WHERE id=?",i%2==0?"DEPLOYED":"REGISTERED",i%2==0?"WORKING":"UNKNOWN",a.id());}
  String field=switch(sort){case "tag"->"assetTag";default->sort;};
  for(String dir:List.of("asc","desc")){String previous=null;var seen=new HashSet<String>();for(int page=0;page<4;page++){var r=list(Map.of("q","sort fixture","sort",sort,"direction",dir,"page",String.valueOf(page),"size","20"));assertEquals(65,total(r));for(var row:items(r)){assertTrue(seen.add((String)row.get("id")));String value=(String)row.get(field);if(previous!=null)assertTrue(dir.equals("asc")?previous.compareTo(value)<=0:previous.compareTo(value)>=0);previous=value;}}assertEquals(expected,seen);}
 }
 @Test void tenantContextNeverGrantsAuthorityAndDetailNonDisclosure()throws Exception{
  var a=asset();UUID foreignLoc=places.create(PlaceService.Kind.LOCATION,other,"Foreign",actor);
  var foreign=assets.register(other,new AssetService.Registration(new AssetService.ProductSelection(product),foreignLoc,null,false,null,null,null),actor);
  var who=principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE);
  for(UUID supplied:List.of(other,UUID.randomUUID(),tenant)){
   var r=request(who,get("/api/v1/tenants/"+supplied+"/assets"),null);assertEquals(200,status(r));assertEquals(1,total(r));assertEquals(a.id().toString(),items(r).getFirst().get("id"));
   r=request(who,post("/api/v1/tenants/"+supplied+"/assets"),Map.of("productId",product,"locationId",location,"loanable",false));assertEquals(201,status(r));assertEquals(tenant.toString(),data(r).get("tenantId"));UUID created=UUID.fromString((String)data(r).get("id"));assertEquals(who.id(),assets.get(tenant,created).createdBy());assets.delete(tenant,created,0,actor);
  }
  for(UUID id:List.of(foreign.id(),UUID.randomUUID())){assertEquals(404,status(request(who,get("/api/v1/assets/"+id),null)));assertEquals(404,status(request(who,get("/api/v1/assets/"+id+"/events"),null)));assertEquals(404,status(request(who,put("/api/v1/assets/"+id+"/metadata"),Map.of("expectedVersion",0,"note","forged"))));}
  assertEquals(404,status(request(who,get(path()).param("locationId",foreignLoc.toString()),null)));
  assertEquals(200,status(request(admin,get("/api/v1/assets").param("tenantId",other.toString()),null)));assertEquals(1,total(request(admin,get("/api/v1/assets").param("tenantId",other.toString()),null)));
  assertEquals(403,status(request(who,get("/api/v1/assets"),null)));
 }
 @Test void typedDetailAssociationsAndConciseListWithoutNplusOne()throws Exception{
  var a=asset();var r=request(admin,get("/api/v1/assets/"+a.id()),null);assertEquals(200,status(r));var d=data(r);
  for(var pair:Map.of("tenant",tenant,"category",category,"manufacturer",manufacturer,"product",product,"location",location,"owner",owner).entrySet()){var ref=(Map<?,?>)d.get(pair.getKey());assertEquals(pair.getValue().toString(),ref.get("id"));assertNotNull(ref.get("name"));}
  assertEquals(actor.id().toString(),d.get("createdBy"));assertFalse(items(list(Map.of())).getFirst().containsKey("note"));assertFalse(d.containsKey("passwordHash"));assertFalse(d.containsKey("deletedBy"));
  var direct=assets.register(tenant,new AssetService.Registration(new AssetService.DirectSelection(category,manufacturer),location,null,false,null,null,null),actor);
  d=data(request(admin,get("/api/v1/assets/"+direct.id()),null));assertNull(d.get("product"));assertNull(d.get("owner"));
  assertEquals(404,status(request(admin,get("/api/v1/assets/"+UUID.randomUUID()),null)));
  assertEquals(404,status(request(admin,delete("/api/v1/assets/"+a.id()),null)));
 }
 @Test void immutableHistoryDefaultsAndFixedTimeline()throws Exception{
  var a=asset();for(int i=0;i<24;i++){clock.now=clock.now.plusSeconds(1);assets.editText(tenant,a.id(),i,"S",null,"note"+i,actor);}
  var r=request(admin,get("/api/v1/assets/"+a.id()+"/events"),null);assertEquals(200,status(r));assertEquals(20,data(r).get("size"));assertEquals(25,total(r));assertEquals("UPDATED",items(r).getFirst().get("type"));
  var e=items(r).getFirst();assertEquals(actor.id().toString(),e.get("actorId"));assertEquals(actor.displayName(),e.get("actorDisplayName"));assertTrue(((Map<?,?>)e.get("data")).containsKey("before"));
  r=request(admin,get("/api/v1/assets/"+a.id()+"/events").param("page","1"),null);assertEquals(5,items(r).size());assertEquals("REGISTERED",items(r).getLast().get("type"));
  for(String key:List.of("q","sort","direction","actorId","from"))assertEquals(400,status(request(admin,get("/api/v1/assets/"+a.id()+"/events").param(key,"text"),null)));
  r=request(admin,get("/api/v1/assets/"+a.id()+"/events").param("page","2"),null);assertTrue(items(r).isEmpty());assertEquals(25,total(r));
 }
 @ParameterizedTest @ValueSource(strings={"createdBy","createdAt","updatedBy","updatedAt","actorId","tenantId","assetTag","version","deletedAt"}) void metadataOverpostingRejected(String key)throws Exception{
  var body=new LinkedHashMap<String,Object>(Map.of("productId",product,"locationId",location,"loanable",true));body.put(key,"forged");assertEquals(400,status(request(admin,post(path()),body)));
  var a=asset();body=new LinkedHashMap<>(Map.of("expectedVersion",0,"note","edit"));body.put(key,"forged");assertEquals(400,status(request(admin,put("/api/v1/assets/"+a.id()+"/metadata"),body)));assertEquals(1,events(a.id()));assertEquals(0,assets.get(tenant,a.id()).version());
 }
 @Test void commandResponseMappingKeepsMetadataAndEventAtomicity()throws Exception{
  var a=asset();clock.now=clock.now.plusSeconds(30);var who=principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE);
  var r=request(who,put("/api/v1/assets/"+a.id()+"/metadata"),Map.of("expectedVersion",0,"note","New"));assertEquals(200,status(r));assertEquals(a.createdAt().toString(),data(r).get("createdAt"));assertEquals(actor.id().toString(),data(r).get("createdBy"));assertEquals(who.id().toString(),data(r).get("updatedBy"));assertEquals(clock.now.toString(),data(r).get("updatedAt"));assertEquals(2,events(a.id()));
  var tx=new TransactionTemplate(tm);assertThrows(IllegalStateException.class,()->tx.execute(s->{assets.editText(tenant,a.id(),1,"rollback",null,null,actor);throw new IllegalStateException("rollback");}));assertEquals(1,assets.get(tenant,a.id()).version());assertEquals("New",assets.get(tenant,a.id()).note());assertEquals(2,events(a.id()));
 }
 @ParameterizedTest @ValueSource(strings={"assetTag","serialNumber","localIdentifier","externalInventoryNumber"}) void qMatchesEachApprovedIdentifierOnly(String field)throws Exception{
  var a=asset();String token="unique-"+UUID.randomUUID();
  if(field.equals("assetTag"))token=a.assetTag();else{String column=switch(field){case "serialNumber"->"serial_number";case "localIdentifier"->"local_identifier";default->"external_inventory_number";};jdbc.update("UPDATE asset SET "+column+"=? WHERE id=?",token,a.id());}
  var r=list(Map.of("q",token));assertEquals(1,total(r));assertEquals(a.id().toString(),items(r).getFirst().get("id"));
  assertEquals(0,total(list(Map.of("q","Room"))));assertEquals(0,total(list(Map.of("q","Category"))));
 }
 @Test void referencesAreIdsRetainedReadsAndFreshAssignmentGuards()throws Exception{
  var a=asset();UUID duplicate=places.create(PlaceService.Kind.LOCATION,tenant,"Room",actor);
  assertEquals(0,total(list(Map.of("locationId",duplicate.toString()))));assertEquals(1,total(list(Map.of("locationId",location.toString()))));
  jdbc.update("UPDATE location SET deleted_at=?,deleted_by=? WHERE id=?",java.sql.Timestamp.from(clock.now),actor.id(),location);
  assertEquals(1,total(list(Map.of("locationId",location.toString()))));var r=request(admin,get("/api/v1/assets/"+a.id()),null);assertEquals("Room",((Map<?,?>)data(r).get("location")).get("name"));
  r=request(admin,post(path()),Map.of("productId",product,"locationId",location,"loanable",false));assertEquals(409,status(r));assertEquals("DELETED_TARGET",data(r).get("code"));assertEquals(1,events(a.id()));
 }
 @Test void detailAndHistoryReadsCreateNoAuditOrEvents()throws Exception{
  var a=asset();long audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
  assertEquals(200,status(request(admin,get("/api/v1/assets/"+a.id()),null)));assertEquals(200,status(request(admin,get("/api/v1/assets/"+a.id()+"/events"),null)));list(Map.of());
  assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));assertEquals(1,events(a.id()));
 }

}
