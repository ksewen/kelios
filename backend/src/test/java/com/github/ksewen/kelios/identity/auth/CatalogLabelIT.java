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
class CatalogLabelIT {
 static class MutableClock extends Clock {
  Instant now=Instant.parse("2026-10-11T12:00:00Z");
  public Instant instant(){return now;} public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
 }
 @org.springframework.boot.test.context.TestConfiguration static class Time {
  @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary MutableClock labelClock(){return new MutableClock();}
 }
 @Autowired MutableClock clock; @Autowired CatalogLabelOperations labels;
 @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17-alpine");
 static final AuthTestKeys KEYS;static{try{KEYS=new AuthTestKeys("catalog-label-keys");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
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
 String plural(CatalogLabel.Kind k){return k==CatalogLabel.Kind.CATEGORY?"categories":"manufacturers";}
 String root(CatalogLabel.Kind k){return "/api/v1/catalog/"+plural(k);}
 String contextual(CatalogLabel.Kind k,UUID t){return "/api/v1/tenants/"+t+"/catalog/"+plural(k);}
 UUID create(CatalogLabel.Kind k,UUID t,String name){return k==CatalogLabel.Kind.CATEGORY?catalog.createCategory(t,name,actor):catalog.createManufacturer(t,name,actor);}
 String prefix(){return "query-"+UUID.randomUUID()+"-";}
 @SuppressWarnings("unchecked") List<Map<String,Object>> items(MvcResult r){return (List<Map<String,Object>>)data(r).get("items");}
 long total(MvcResult r){return ((Number)data(r).get("totalElements")).longValue();}
 @SuppressWarnings("unchecked") Map<String,Object> metadata(MvcResult r){return (Map<String,Object>)data(r).get("metadata");}
 @Test void categoryActorNamesAreBoundedToVisibleRowsAndSurviveMissingIdentity()throws Exception{
  UUID creator=UUID.randomUUID();var now=java.sql.Timestamp.from(clock.now);
  jdbc.update("INSERT INTO app_user(id,scope,tenant_id,role,status,display_name,created_by,created_at,updated_by,updated_at) VALUES(?,'GLOBAL',NULL,'USER','ACTIVE',?,?,?,?,?)",
      creator,"Current display name",actor.id(),now,actor.id(),now);
  UUID id=catalog.createCategory(null,"Actor projection "+UUID.randomUUID(),new Actor(creator,"Creation snapshot"));
  var reader=principal(Scope.GLOBAL,Role.USER,Status.ACTIVE);
  var detail=request(reader,get(root(CatalogLabel.Kind.CATEGORY)+"/"+id),null);
  assertEquals(200,status(detail));assertEquals("Creation snapshot",metadata(detail).get("createdByDisplayName"));
  assertEquals("Current display name",metadata(detail).get("updatedByDisplayName"));
  var rows=items(list(CatalogLabel.Kind.CATEGORY,null,Map.of("q",(String)data(detail).get("name"))));
  assertEquals("Creation snapshot",((Map<?,?>)rows.getFirst().get("metadata")).get("createdByDisplayName"));
  UUID manufacturerId=catalog.createManufacturer(null,"Unchanged manufacturer actor "+UUID.randomUUID(),new Actor(creator,"Creation snapshot"));
  assertEquals("Creation snapshot",metadata(request(reader,get(root(CatalogLabel.Kind.MANUFACTURER)+"/"+manufacturerId),null)).get("createdByDisplayName"));
  assertEquals("Creation snapshot",((Map<?,?>)items(list(CatalogLabel.Kind.MANUFACTURER,null,Map.of("q","Unchanged manufacturer actor"))).getFirst().get("metadata")).get("createdByDisplayName"));
  jdbc.update("UPDATE app_user SET display_name='Renamed actor' WHERE id=?",creator);
  assertEquals("Creation snapshot",metadata(request(reader,get(root(CatalogLabel.Kind.CATEGORY)+"/"+id),null)).get("createdByDisplayName"));
  jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=?",creator);
  assertEquals("Creation snapshot",metadata(request(reader,get(root(CatalogLabel.Kind.CATEGORY)+"/"+id),null)).get("createdByDisplayName"));
  jdbc.update("DELETE FROM app_user WHERE id=?",creator);
  assertEquals("Creation snapshot",metadata(request(reader,get(root(CatalogLabel.Kind.CATEGORY)+"/"+id),null)).get("createdByDisplayName"));
  assertEquals("Creation snapshot",metadata(request(reader,get(root(CatalogLabel.Kind.MANUFACTURER)+"/"+manufacturerId),null)).get("createdByDisplayName"));
  var afterRemoval=request(reader,get(root(CatalogLabel.Kind.CATEGORY)).param("q",(String)data(detail).get("name")),null);
  assertEquals(200,status(afterRemoval));assertEquals(1,total(afterRemoval));assertEquals(id.toString(),items(afterRemoval).getFirst().get("id"));
  assertEquals("Creation snapshot",((Map<?,?>)items(afterRemoval).getFirst().get("metadata")).get("createdByDisplayName"));
  assertFalse(items(afterRemoval).getFirst().containsKey("globalAccount"));
  assertFalse(items(afterRemoval).getFirst().containsKey("email"));
  jdbc.update("UPDATE category SET created_by_display_name=NULL WHERE id=?",id);
  assertNull(metadata(request(reader,get(root(CatalogLabel.Kind.CATEGORY)+"/"+id),null)).get("createdByDisplayName"));
  assertEquals(SystemActor.DISPLAY_NAME,metadata(request(reader,get(root(CatalogLabel.Kind.CATEGORY)+"/00000000-0000-0000-0000-000000000001"),null)).get("createdByDisplayName"));
 }
 MvcResult list(CatalogLabel.Kind k,UUID t,Map<String,String> query)throws Exception{
  var b=get(t==null?root(k):contextual(k,t));query.forEach(b::param);var r=request(admin,b,null);assertEquals(200,status(r),r.getResponse().getContentAsString());return r;
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void defaultsAndServerDefaultOrder(CatalogLabel.Kind k)throws Exception{
  String p=prefix();UUID first=create(k,null,p+"A");clock.now=clock.now.plusSeconds(1);UUID latest=create(k,null,p+"Z");
  var r=list(k,null,Map.of("q",p));assertEquals(0,data(r).get("page"));assertEquals(20,data(r).get("size"));assertEquals(latest.toString(),items(r).getFirst().get("id"));assertEquals(first.toString(),items(r).getLast().get("id"));
  assertEquals(25,ListInput.size(Map.of()),"Other resources' default is separately gated");
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void allSupportedSizesAndBeyondEnd(CatalogLabel.Kind k)throws Exception{
  String p=prefix();for(int i=0;i<105;i++)create(k,null,p+i);
  for(int size:List.of(1,10,20,30,40,50,100)){
   var r=list(k,null,Map.of("q",p,"size",String.valueOf(size)));assertEquals(size,items(r).size());assertEquals(size,data(r).get("size"));assertEquals(105,total(r));
  }
  var r=list(k,null,Map.of("q",p,"page","2147483647","size","100"));assertTrue(items(r).isEmpty());assertEquals(2147483647,data(r).get("page"));assertEquals(105,total(r));
  var empty=list(k,null,Map.of("q",prefix(),"page","2"));assertTrue(items(empty).isEmpty());assertEquals(0,total(empty));assertEquals(2,data(empty).get("page"));
 }
 static java.util.stream.Stream<Arguments> invalidQueries(){
  return java.util.stream.Stream.of(new String[]{"page","-1"},new String[]{"page","2147483648"},new String[]{"page","1.5"},new String[]{"size","0"},new String[]{"size","101"},new String[]{"size","abc"},new String[]{"sort","createdAt"},new String[]{"sort","manufacturerNumber"},new String[]{"sort","name; DROP TABLE category"},new String[]{"direction","up"},new String[]{"direction","desc"},new String[]{"visibility","PUBLIC"},new String[]{"deleted","true"},new String[]{"deleted","all"},new String[]{"deleted","false"},new String[]{"unpaged","true"},new String[]{"tenantId",UUID.randomUUID().toString()})
   .flatMap(v->java.util.Arrays.stream(CatalogLabel.Kind.values()).map(k->Arguments.of(k,v[0],v[1])));
 }
 @ParameterizedTest @MethodSource("invalidQueries") void strictQueryValidation(CatalogLabel.Kind k,String key,String value)throws Exception{
  var r=request(admin,get(root(k)).param(key,value),null);assertEquals(400,status(r));assertEquals("INVALID_INPUT",data(r).get("code"));assertTrue(r.getResponse().getContentType().startsWith("application/problem+json"));assertEquals("no-store",r.getResponse().getHeader("Cache-Control"));
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void normalizedLiteralSearchAndUtf16Limit(CatalogLabel.Kind k)throws Exception{
  String p=prefix();create(k,null,p+"Percent%_\\Case");create(k,null,p+"PercentXxCase");
  var r=list(k,null,Map.of("q","  "+p.toUpperCase()+"percent%_\\  "));assertEquals(1,total(r));
  assertEquals(total(list(k,null,Map.of())),total(list(k,null,Map.of("q"," ".repeat(200)))));
  create(k,null,"x".repeat(128));assertTrue(total(list(k,null,Map.of("q"," "+"x".repeat(128)+" ")))>0);
  assertEquals(400,status(request(admin,get(root(k)).param("q","x".repeat(129)),null)));
  assertEquals(400,status(request(admin,get(root(k)).param("q","😀".repeat(65)),null)));
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void fullCollectionSortBeforePagingAndDuplicateBoundaries(CatalogLabel.Kind k)throws Exception{
  String p=prefix();var ids=new HashSet<String>();for(int i=0;i<67;i++)ids.add(create(k,null,p+(char)('A'+i%3)).toString());
  for(String direction:List.of("asc","desc")){
   var seen=new HashSet<String>();String previous=null;
   for(int page=0;page<4;page++){
    var r=list(k,null,Map.of("q",p,"sort","name","direction",direction,"page",String.valueOf(page)));assertEquals(67,total(r));
    for(var row:items(r)){String name=(String)row.get("name");if(previous!=null)assertTrue(direction.equals("asc")?previous.compareTo(name)<=0:previous.compareTo(name)>=0);previous=name;assertTrue(seen.add((String)row.get("id")),"No overlap across pages");}
   }assertEquals(ids,seen,"No omitted duplicate primary values across >=3 pages");
  }
  assertEquals(p+"A",items(list(k,null,Map.of("q",p,"sort","name","size","1"))).getFirst().get("name"));
  assertEquals(p+"C",items(list(k,null,Map.of("q",p,"sort","name","direction","desc","size","1"))).getFirst().get("name"));
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void combinedScopeSearchVisibilitySortCountAndPage(CatalogLabel.Kind k)throws Exception{
  String p=prefix();for(int i=0;i<4;i++){create(k,null,p+"G"+i);create(k,tenant,p+"T"+i);create(k,other,p+"Foreign"+i);}
  UUID deleted=create(k,tenant,p+"Deleted");catalog.delete(CatalogService.Kind.valueOf(k.name()),deleted,actor);
  var r=list(k,tenant,Map.of("q",p,"sort","visibility","direction","asc","size","2"));assertEquals(8,total(r));assertTrue(items(r).stream().allMatch(x->x.get("visibility").equals("GLOBAL")));
  r=list(k,tenant,Map.of("q",p,"sort","visibility","direction","desc","visibility","TENANT","page","1","size","2"));assertEquals(4,total(r));assertEquals(2,items(r).size());assertTrue(items(r).stream().allMatch(x->x.get("tenantId").equals(tenant.toString())));
  assertEquals(12,total(list(k,null,Map.of("q",p))));
  assertEquals(8,total(list(k,null,Map.of("q",p,"visibility","TENANT"))));
 }
 @Test void categoryGlobalContextCountsSearchesFiltersAndSortsAllTenantsBeforePaging()throws Exception{
  String p=prefix();var ids=new HashSet<String>();
  for(int i=0;i<23;i++){
   ids.add(create(CatalogLabel.Kind.CATEGORY,null,p+"Global "+i).toString());
   ids.add(create(CatalogLabel.Kind.CATEGORY,tenant,p+"North "+i).toString());
   ids.add(create(CatalogLabel.Kind.CATEGORY,other,p+"South "+i).toString());
  }
  UUID deleted=create(CatalogLabel.Kind.CATEGORY,other,p+"Deleted");
  catalog.delete(CatalogService.Kind.CATEGORY,deleted,actor);
  var rootResult=list(CatalogLabel.Kind.CATEGORY,null,Map.of("q",p));
  assertEquals(69,total(rootResult));assertEquals(20,items(rootResult).size());
  assertEquals(23,total(list(CatalogLabel.Kind.CATEGORY,null,Map.of("q",p+"South"))));
  assertEquals(46,total(list(CatalogLabel.Kind.CATEGORY,null,Map.of("q",p,"visibility","TENANT"))));
  assertEquals(23,total(list(CatalogLabel.Kind.CATEGORY,null,Map.of("q",p,"visibility","GLOBAL"))));
  for(String sort:List.of("name","tenantName"))for(String direction:List.of("asc","desc")){
   var seen=new HashSet<String>();String previous=null;
   for(int page=0;page<4;page++){
    var result=list(CatalogLabel.Kind.CATEGORY,null,Map.of("q",p,"sort",sort,"direction",direction,"page",String.valueOf(page)));
    assertEquals(69,total(result));
    for(var item:items(result)){
     String value=sort.equals("name")?(String)item.get("name"):item.get("tenant")==null?"Global":(String)((Map<?,?>)item.get("tenant")).get("name");
     if(previous!=null)assertTrue(direction.equals("asc")?previous.compareTo(value)<=0:previous.compareTo(value)>=0);
     previous=value;assertTrue(seen.add((String)item.get("id")));
    }
   }
   assertEquals(ids,seen);
  }
  assertEquals(46,total(list(CatalogLabel.Kind.CATEGORY,tenant,Map.of("q",p))));
  assertEquals(23,total(list(CatalogLabel.Kind.CATEGORY,tenant,Map.of("q",p,"visibility","TENANT"))));
  var own=new Identity(UUID.randomUUID(),Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE,"Tenant admin");
  var ownResult=request(own,get(root(CatalogLabel.Kind.CATEGORY)).param("q",p),null);
  assertEquals(200,status(ownResult));assertEquals(46,total(ownResult));
  assertTrue(items(ownResult).stream().noneMatch(item->other.toString().equals(item.get("tenantId"))));
 }
 @Test void manufacturerRootAggregatesAuthorizedTenantsBeforeSearchFilterSortAndPaging()throws Exception{
  var k=CatalogLabel.Kind.MANUFACTURER;String p=prefix();var ids=new HashSet<String>();
  for(int i=0;i<24;i++){
   ids.add(create(k,null,p+"Global "+i).toString());
   ids.add(create(k,tenant,p+"North "+i).toString());
   ids.add(create(k,other,p+"South "+i).toString());
  }
  UUID deleted=create(k,other,p+"Deleted");catalog.delete(CatalogService.Kind.MANUFACTURER,deleted,actor);
  assertEquals(72,total(list(k,null,Map.of("q",p))));
  assertEquals(24,total(list(k,null,Map.of("q",p+"South"))));
  assertEquals(48,total(list(k,null,Map.of("q",p,"visibility","TENANT"))));
  assertEquals(24,total(list(k,null,Map.of("q",p,"visibility","GLOBAL"))));
  for(String direction:List.of("asc","desc")){
   var seen=new HashSet<String>();String previous=null;
   for(int page=0;page<4;page++)for(var item:items(list(k,null,Map.of("q",p,"sort","name","direction",direction,"page",String.valueOf(page))))){
    String name=(String)item.get("name");if(previous!=null)assertTrue(direction.equals("asc")?previous.compareTo(name)<=0:previous.compareTo(name)>=0);
    previous=name;assertTrue(seen.add((String)item.get("id")));
   }
   assertEquals(ids,seen);
  }
  assertEquals(48,total(list(k,tenant,Map.of("q",p))));
  var own=new Identity(UUID.randomUUID(),Scope.TENANT,tenant,Role.USER,Status.ACTIVE,"Tenant reader");
  var result=request(own,get(root(k)).param("q",p),null);assertEquals(200,status(result));assertEquals(48,total(result));
  assertTrue(items(result).stream().noneMatch(item->other.toString().equals(item.get("tenantId"))));
 }
 @Test void categoryTenantNameSortUsesVisibleOwnershipBeforePaging()throws Exception{
  String p=prefix();var ids=new HashSet<String>();
  for(int i=0;i<27;i++){
   ids.add(create(CatalogLabel.Kind.CATEGORY,null,p+"G"+i).toString());
   ids.add(create(CatalogLabel.Kind.CATEGORY,tenant,p+"T"+i).toString());
  }
  for(String direction:List.of("asc","desc")){
   var seen=new HashSet<String>();String previous=null;
   for(int page=0;page<4;page++){
    var result=list(CatalogLabel.Kind.CATEGORY,tenant,Map.of("q",p,"sort","tenantName","direction",direction,"size","20","page",String.valueOf(page)));
    assertEquals(54,total(result));
    for(var row:items(result)){
     String owner=row.get("tenant")==null?"Global":(String)((Map<?,?>)row.get("tenant")).get("name");
     if(previous!=null)assertTrue(direction.equals("asc")?previous.compareTo(owner)<=0:previous.compareTo(owner)>=0);
     previous=owner;assertTrue(seen.add((String)row.get("id")),"No overlap across tenant-name pages");
    }
   }
   assertEquals(ids,seen);
  }
  UUID alphabeticallyFirstTenant=tenants.create("Aardvark",actor);
  String ownerPrefix=prefix();UUID localFirst=create(CatalogLabel.Kind.CATEGORY,alphabeticallyFirstTenant,ownerPrefix+"Local");
  UUID globalSecond=create(CatalogLabel.Kind.CATEGORY,null,ownerPrefix+"Global");
  var byName=list(CatalogLabel.Kind.CATEGORY,alphabeticallyFirstTenant,Map.of("q",ownerPrefix,"sort","tenantName","size","1"));
  assertEquals(2,total(byName));assertEquals(localFirst.toString(),items(byName).getFirst().get("id"));
  byName=list(CatalogLabel.Kind.CATEGORY,alphabeticallyFirstTenant,Map.of("q",ownerPrefix,"sort","tenantName","direction","desc","size","1"));
  assertEquals(globalSecond.toString(),items(byName).getFirst().get("id"));
  assertEquals(400,status(request(admin,get(root(CatalogLabel.Kind.MANUFACTURER)).param("sort","tenantName"),null)));
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void tenantTransportIdsNeverDetermineReadOrCreationScope(CatalogLabel.Kind k)throws Exception{
  String p=prefix();UUID own=create(k,tenant,p+"Own"),foreign=create(k,other,p+"Foreign"),global=create(k,null,p+"Global");
  var local=new Identity(UUID.randomUUID(),Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE,"Tenant admin");
  for(UUID supplied:List.of(other,UUID.randomUUID(),tenant)){
   var r=request(local,get(contextual(k,supplied)).param("q",p),null);assertEquals(200,status(r));assertEquals(2,total(r));assertEquals(Set.of(own.toString(),global.toString()),items(r).stream().map(x->(String)x.get("id")).collect(java.util.stream.Collectors.toSet()));
   var made=request(local,post(contextual(k,supplied)),Map.of("name","Created outside read query"));assertEquals(201,status(made));UUID id=UUID.fromString((String)data(made).get("id"));assertEquals(tenant,catalog.read(CatalogService.Kind.valueOf(k.name()),id).tenantId());
  }
  var rootCreate=request(local,post(root(k)),Map.of("name",p+"Root"));assertEquals(201,status(rootCreate));assertEquals(tenant,catalog.read(CatalogService.Kind.valueOf(k.name()),UUID.fromString((String)data(rootCreate).get("id"))).tenantId());
  assertEquals(404,status(request(local,get(root(k)+"/"+foreign),null)));assertEquals(404,status(request(local,get(root(k)+"/"+UUID.randomUUID()),null)));assertEquals(404,status(request(local,put(root(k)+"/"+foreign),Map.of("name","Attack"))));assertEquals(403,status(request(local,put(root(k)+"/"+global),Map.of("name","Attack"))));
  assertEquals(200,status(request(admin,get(root(k)+"/"+foreign),null)));assertEquals(404,status(request(admin,get(contextual(k,UUID.randomUUID())),null)));
 }
 @Test void categoryCreateOwnershipUsesRouteOnlyForGlobalAdminAndSignedTenantForTenantAdmin()throws Exception{
  var global=request(admin,post(root(CatalogLabel.Kind.CATEGORY)),Map.of("name","Global laptop"));assertEquals(201,status(global));
  UUID globalId=UUID.fromString((String)data(global).get("id"));assertNull(catalog.read(CatalogService.Kind.CATEGORY,globalId).tenantId());
  var concrete=request(admin,post(contextual(CatalogLabel.Kind.CATEGORY,other)),Map.of("name","Tenant laptop"));assertEquals(201,status(concrete));
  UUID concreteId=UUID.fromString((String)data(concrete).get("id"));assertEquals(other,catalog.read(CatalogService.Kind.CATEGORY,concreteId).tenantId());
  var ownAdmin=principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE);
  var own=request(ownAdmin,post(contextual(CatalogLabel.Kind.CATEGORY,tenant)),Map.of("name","Own laptop"));assertEquals(201,status(own));
  UUID ownId=UUID.fromString((String)data(own).get("id"));assertEquals(tenant,catalog.read(CatalogService.Kind.CATEGORY,ownId).tenantId());
  var foreignPath=request(ownAdmin,post(contextual(CatalogLabel.Kind.CATEGORY,other)),Map.of("name","Still own"));assertEquals(201,status(foreignPath));
  assertEquals(tenant,catalog.read(CatalogService.Kind.CATEGORY,UUID.fromString((String)data(foreignPath).get("id"))).tenantId());
  var rootPath=request(ownAdmin,post(root(CatalogLabel.Kind.CATEGORY)),Map.of("name","Still own root"));assertEquals(201,status(rootPath));
  assertEquals(tenant,catalog.read(CatalogService.Kind.CATEGORY,UUID.fromString((String)data(rootPath).get("id"))).tenantId());
  assertEquals(403,status(request(principal(Scope.GLOBAL,Role.USER,Status.ACTIVE),post(root(CatalogLabel.Kind.CATEGORY)),Map.of("name","Denied"))));
  assertEquals(403,status(request(principal(Scope.TENANT,Role.USER,Status.ACTIVE),post(contextual(CatalogLabel.Kind.CATEGORY,tenant)),Map.of("name","Denied"))));
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void independentDetailAndDtoProjection(CatalogLabel.Kind k)throws Exception{
  UUID id=create(k,tenant,"Readable detail");long audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
  var r=request(admin,get(root(k)+"/"+id),null);assertEquals(200,status(r));var d=data(r);assertEquals(id.toString(),d.get("id"));assertEquals("TENANT",d.get("visibility"));assertEquals(tenant.toString(),((Map<?,?>)d.get("tenant")).get("id"));assertTrue(((Map<?,?>)d.get("tenant")).get("name").toString().startsWith("Tenant"));assertTrue(d.containsKey("metadata"));
  assertFalse(d.containsKey("categoryId"));assertFalse(d.containsKey("manufacturerId"));assertFalse(d.containsKey("manufacturerNumber"));assertFalse(d.containsKey("passwordHash"));
  var row=items(list(k,tenant,Map.of("q","Readable detail"))).getFirst();assertFalse(row.containsKey("deletedAt"));assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  catalog.delete(CatalogService.Kind.valueOf(k.name()),id,actor);assertEquals(0,total(list(k,tenant,Map.of("q","Readable detail"))));assertEquals(true,data(request(admin,get(root(k)+"/"+id),null)).get("deleted"));
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void creationEditPublicationDeletionAndMetadata(CatalogLabel.Kind k)throws Exception{
  var r=request(admin,post(contextual(k,tenant)),Map.of("name","  Same name  "));assertEquals(201,status(r));UUID id=UUID.fromString((String)data(r).get("id"));assertEquals(root(k)+"/"+id,r.getResponse().getHeader("Location"));
  var before=data(request(admin,get(root(k)+"/"+id),null));clock.now=clock.now.plusSeconds(10);
  assertEquals(204,status(request(admin,put(root(k)+"/"+id),Map.of("name"," Edited "))));var after=data(request(admin,get(root(k)+"/"+id),null));var a=(Map<?,?>)before.get("metadata");var b=(Map<?,?>)after.get("metadata");assertEquals(a.get("createdAt"),b.get("createdAt"));assertEquals(a.get("createdBy"),b.get("createdBy"));assertNotEquals(a.get("updatedAt"),b.get("updatedAt"));assertEquals(actor.id().toString(),b.get("updatedBy"));
  assertEquals(204,status(request(admin,post(root(k)+"/"+id+"/publish"),Map.of())));after=data(request(admin,get(root(k)+"/"+id),null));assertEquals("GLOBAL",after.get("visibility"));assertNull(after.get("tenant"));assertEquals(id.toString(),after.get("id"));assertEquals(tenant,attribution(k+"_PUBLISHED",id));
  assertEquals(204,status(request(admin,delete(root(k)+"/"+id),null)));assertEquals(true,data(request(admin,get(root(k)+"/"+id),null)).get("deleted"));assertEquals(409,status(request(admin,put(root(k)+"/"+id),Map.of("name","Deleted edit"))));
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void combinedSaveAndPublishOnlyRetainGuardsAndAudit(CatalogLabel.Kind k)throws Exception{
  UUID combined=create(k,tenant,"Before combined");
  assertEquals(204,status(request(admin,put(root(k)+"/"+combined),Map.of("name","After combined","publishGlobally",true))));
  var result=data(request(admin,get(root(k)+"/"+combined),null));assertEquals("After combined",result.get("name"));assertEquals("GLOBAL",result.get("visibility"));
  assertEquals(tenant,attribution(k+"_EDITED",combined));assertEquals(tenant,attribution(k+"_PUBLISHED",combined));
  UUID referenced=create(k,tenant,"Referenced publish only");
  if(k==CatalogLabel.Kind.CATEGORY)catalog.createProduct(tenant,referenced,manufacturer,"Dependent",null,actor);
  else catalog.createProduct(tenant,category,referenced,"Dependent",null,actor);
  assertEquals(204,status(request(admin,put(root(k)+"/"+referenced),Map.of("name","Referenced publish only","publishGlobally",true))));
  assertEquals("GLOBAL",data(request(admin,get(root(k)+"/"+referenced),null)).get("visibility"));
  assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action=? AND target_id=?",Long.class,k+"_EDITED",referenced));
  assertEquals(tenant,attribution(k+"_PUBLISHED",referenced));
  UUID blocked=create(k,tenant,"Blocked combined");
  if(k==CatalogLabel.Kind.CATEGORY)catalog.createProduct(tenant,blocked,manufacturer,"Guarded",null,actor);
  else catalog.createProduct(tenant,category,blocked,"Guarded",null,actor);
  assertEquals(409,status(request(admin,put(root(k)+"/"+blocked),Map.of("name","Changed","publishGlobally",true))));
  result=data(request(admin,get(root(k)+"/"+blocked),null));assertEquals("Blocked combined",result.get("name"));assertEquals("TENANT",result.get("visibility"));
  assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action IN (?,?) AND target_id=?",Long.class,k+"_EDITED",k+"_PUBLISHED",blocked));
  assertEquals(403,status(request(principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE),put(root(k)+"/"+create(k,tenant,"Own")),Map.of("name","Own","publishGlobally",true))));
  assertEquals(400,status(request(admin,put(root(k)+"/"+create(k,tenant,"Strict")),Map.of("name","Strict","publishGlobally","true"))));
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void strictCommandDtoRejectsOverpostingAndTypeCoercion(CatalogLabel.Kind k)throws Exception{
  for(String field:List.of("createdBy","createdAt","updatedBy","updatedAt","tenantId","visibility","deletedAt","deletedBy","actor","manufacturerNumber")){
   assertEquals(400,status(request(admin,post(root(k)),Map.of("name","Overposting",field,UUID.randomUUID().toString()))));
  }
  for(Object value:List.of(12,true,List.of("Name")))assertEquals(400,status(request(admin,post(root(k)),Map.of("name",value))));
  assertEquals(400,status(request(admin,post(root(k)+"/merge-preview"),Map.of("sourceId","1-1-1-1-1","targetId",category.toString()))));
  assertEquals(400,status(request(admin,post(root(k)+"/merge-preview"),Map.of("sourceId",category.toString(),"targetId",manufacturer.toString(),"actor","Forged"))));
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void roleStatusAndCommandAuthorization(CatalogLabel.Kind k)throws Exception{
  UUID own=create(k,tenant,"Own"),target=create(k,null,"Target");
  for(Scope scope:Scope.values())for(Role role:Role.values()){
   var who=new Identity(UUID.randomUUID(),scope,scope==Scope.TENANT?tenant:null,role,Status.ACTIVE,"Matrix");
   assertEquals(200,status(request(who,get(root(k)+"/"+own),null)));assertEquals(200,status(request(who,get(contextual(k,tenant)),null)));
   assertEquals(role==Role.ADMIN?201:403,status(request(who,post(contextual(k,tenant)),Map.of("name","Created"))));
   assertEquals(scope==Scope.GLOBAL&&role==Role.ADMIN?200:403,status(request(who,post(root(k)+"/merge-preview"),Map.of("sourceId",own.toString(),"targetId",target.toString()))));
   for(Status state:List.of(Status.PENDING,Status.DISABLED)){
    var blocked=new Identity(who.id(),scope,who.tenantId(),role,state,who.displayName());
    if(state==Status.DISABLED){
     assertThrows(AuthFailure.class,()->tokens.login(blocked));
     org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(blocked,null,List.of()));
     try{
      assertThrows(AuthFailure.class,()->labels.list(k,null,new CatalogLabelQuery(0,20,null,null,null,null)));
      assertThrows(AuthFailure.class,()->labels.detail(k,own));
      assertThrows(AuthFailure.class,()->labels.create(k,tenant,new CatalogLabel.Name("Blocked")));
     }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
     continue;
    }
    assertEquals(403,status(request(blocked,get(root(k)),null)));assertEquals(403,status(request(blocked,get(root(k)+"/"+own),null)));assertEquals(403,status(request(blocked,post(contextual(k,tenant)),Map.of("name","Blocked"))));
   }
  }
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void retainedReferencesUnknownAndDuplicateNames(CatalogLabel.Kind k)throws Exception{
  UUID id=create(k,null,"Duplicate");create(k,null,"Duplicate");UUID existing=k==CatalogLabel.Kind.CATEGORY?category:manufacturer;
  var a=asset();assets.lifecycle(tenant,a.id(),0,LifecycleState.DEREGISTERED,DeregistrationReason.ERROR,null,actor);assets.delete(tenant,a.id(),1,actor);
  assertEquals("REFERENCED",data(request(admin,put(root(k)+"/"+existing),Map.of("name","Blocked"))).get("code"));assertEquals("REFERENCED",data(request(admin,delete(root(k)+"/"+existing),null)).get("code"));
  UUID unknown=k==CatalogLabel.Kind.CATEGORY?CatalogService.UNKNOWN_CATEGORY:CatalogService.UNKNOWN_MANUFACTURER;
  assertEquals("PROTECTED_UNKNOWN",data(request(admin,put(root(k)+"/"+unknown),Map.of("name","Blocked"))).get("code"));assertEquals("PROTECTED_UNKNOWN",data(request(admin,delete(root(k)+"/"+unknown),null)).get("code"));assertEquals("PROTECTED_UNKNOWN",data(request(admin,post(root(k)+"/merge"),Map.of("sourceId",unknown.toString(),"targetId",id.toString()))).get("code"));
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void previewAdvisoryExecutionFreshAndAtomic(CatalogLabel.Kind k)throws Exception{
  UUID source=create(k,null,"Source"),target=create(k,null,"Target");String column=k==CatalogLabel.Kind.CATEGORY?"category_id":"manufacturer_id";
  UUID dependent=catalog.createProduct(null,k==CatalogLabel.Kind.CATEGORY?source:category,k==CatalogLabel.Kind.MANUFACTURER?source:manufacturer,"Dependent",null,actor);
  var a=assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(dependent),location,owner,true,null,null,null),actor);
  long audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);var body=Map.of("sourceId",source.toString(),"targetId",target.toString());
  var p=request(admin,post(root(k)+"/merge-preview"),body);assertEquals(200,status(p));assertEquals(1L,((Number)data(p).get("productCount")).longValue());assertEquals(1L,((Number)data(p).get("assetCount")).longValue());assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  assertEquals(204,status(request(admin,post(root(k)+"/merge"),body)));assertEquals(target,jdbc.queryForObject("SELECT "+column+" FROM product WHERE id=?",UUID.class,dependent));assertEquals(target,jdbc.queryForObject("SELECT "+column+" FROM asset WHERE id=?",UUID.class,a.id()));assertEquals(2,events(a.id()));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action=? AND target_id=?",Integer.class,k+"_MERGED",source));
  UUID s=create(k,null,"Obsolete source"),t=create(k,null,"Obsolete target");body=Map.of("sourceId",s.toString(),"targetId",t.toString());assertEquals(200,status(request(admin,post(root(k)+"/merge-preview"),body)));catalog.delete(CatalogService.Kind.valueOf(k.name()),t,actor);assertEquals(409,status(request(admin,post(root(k)+"/merge"),body)));assertNull(jdbc.queryForObject("SELECT deleted_at FROM "+k.name().toLowerCase()+" WHERE id=?",java.sql.Timestamp.class,s));
 }
 @Test void manufacturerCrossTenantMergeWithoutDependentsIsLegal()throws Exception{
  var k=CatalogLabel.Kind.MANUFACTURER;UUID source=create(k,tenant,"Unused source"),target=create(k,other,"Private survivor");
  var body=Map.of("sourceId",source,"targetId",target);long audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
  var preview=data(request(admin,post(root(k)+"/merge-preview"),body));
  assertEquals(0L,((Number)preview.get("productCount")).longValue());assertEquals(0L,((Number)preview.get("assetCount")).longValue());assertEquals(List.of(),preview.get("blockers"));
  assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  assertEquals(204,status(request(admin,post(root(k)+"/merge"),body)));
  assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM manufacturer WHERE id=?",java.sql.Timestamp.class,source));
  assertNull(jdbc.queryForObject("SELECT deleted_at FROM manufacturer WHERE id=?",java.sql.Timestamp.class,target));
  assertEquals(tenant,attribution("MANUFACTURER_MERGED",source));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='MANUFACTURER_MERGED' AND target_id=?",Integer.class,source));
  assertEquals(audits+1,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
 }
 @Test void manufacturerCrossTenantMergeRedirectsEveryRetainedSurvivorTenantDependent()throws Exception{
  var k=CatalogLabel.Kind.MANUFACTURER;UUID source=create(k,null,"Global source"),target=create(k,other,"Private survivor");
  UUID liveProduct=catalog.createProduct(other,category,source,"Live dependent",null,actor);
  UUID deletedProduct=catalog.createProduct(other,category,source,"Retained deleted dependent",null,actor);
  catalog.delete(CatalogService.Kind.PRODUCT,deletedProduct,actor);
  UUID otherLocation=places.create(PlaceService.Kind.LOCATION,other,"Other room",actor);
  var liveAsset=assets.register(other,new AssetService.Registration(new AssetService.ProductSelection(liveProduct),otherLocation,null,true,null,null,null),actor);
  var deletedAsset=assets.register(other,new AssetService.Registration(new AssetService.DirectSelection(category,source),otherLocation,null,true,null,null,null),actor);
  assets.lifecycle(other,deletedAsset.id(),0,LifecycleState.DEREGISTERED,DeregistrationReason.ERROR,null,actor);
  assets.delete(other,deletedAsset.id(),1,actor);
  long liveEvents=events(liveAsset.id()),deletedEvents=events(deletedAsset.id()),audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
  var body=Map.of("sourceId",source,"targetId",target);var preview=data(request(admin,post(root(k)+"/merge-preview"),body));
  assertEquals(2L,((Number)preview.get("productCount")).longValue());assertEquals(2L,((Number)preview.get("assetCount")).longValue());
  var affected=(List<?>)preview.get("affectedTenants");assertEquals(1,affected.size());
  assertEquals(other.toString(),((Map<?,?>)affected.getFirst()).get("tenantId"));
  assertEquals(4L,((Number)((Map<?,?>)affected.getFirst()).get("referenceCount")).longValue());
  assertEquals(List.of(),preview.get("blockers"));assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  assertEquals(204,status(request(admin,post(root(k)+"/merge"),body)));
  for(UUID id:List.of(liveProduct,deletedProduct))assertEquals(target,jdbc.queryForObject("SELECT manufacturer_id FROM product WHERE id=?",UUID.class,id));
  for(UUID id:List.of(liveAsset.id(),deletedAsset.id()))assertEquals(target,jdbc.queryForObject("SELECT manufacturer_id FROM asset WHERE id=?",UUID.class,id));
  assertEquals(liveEvents+1,events(liveAsset.id()));assertEquals(deletedEvents+1,events(deletedAsset.id()));
  assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM manufacturer WHERE id=?",java.sql.Timestamp.class,source));
  assertNull(attribution("MANUFACTURER_MERGED",source));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='MANUFACTURER_MERGED' AND target_id=?",Integer.class,source));
  assertEquals(audits+1,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
 }
 @ParameterizedTest @ValueSource(strings={"PRODUCT","ASSET"}) void manufacturerCrossTenantMergeRejectsNewSourceTenantDependentAfterPreview(String dependent)throws Exception{
  var k=CatalogLabel.Kind.MANUFACTURER;UUID source=create(k,tenant,"Private source"),target=create(k,other,"Foreign survivor");
  var body=Map.of("sourceId",source,"targetId",target);var preview=data(request(admin,post(root(k)+"/merge-preview"),body));
  assertEquals(List.of(),preview.get("blockers"));assertEquals(0L,((Number)preview.get("productCount")).longValue());assertEquals(0L,((Number)preview.get("assetCount")).longValue());
  UUID productId=null,assetId=null;
  if(dependent.equals("PRODUCT"))productId=catalog.createProduct(tenant,category,source,"New source dependent",null,actor);
  else assetId=assets.register(tenant,new AssetService.Registration(new AssetService.DirectSelection(category,source),location,null,true,null,null,null),actor).id();
  long audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class),beforeEvents=assetId==null?0:events(assetId);
  var updatedPreview=data(request(admin,post(root(k)+"/merge-preview"),body));
  assertEquals(List.of("Target is not visible to every retained dependent"),updatedPreview.get("blockers"));
  assertEquals(dependent.equals("PRODUCT")?1L:0L,((Number)updatedPreview.get("productCount")).longValue());
  assertEquals(dependent.equals("ASSET")?1L:0L,((Number)updatedPreview.get("assetCount")).longValue());
  assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  var rejected=request(admin,post(root(k)+"/merge"),body);
  assertEquals(409,status(rejected));assertEquals("MERGE_REQUIRES_GLOBAL_TARGET",data(rejected).get("code"));
  if(productId!=null)assertEquals(source,jdbc.queryForObject("SELECT manufacturer_id FROM product WHERE id=?",UUID.class,productId));
  if(assetId!=null){assertEquals(source,jdbc.queryForObject("SELECT manufacturer_id FROM asset WHERE id=?",UUID.class,assetId));assertEquals(beforeEvents,events(assetId));}
  assertNull(jdbc.queryForObject("SELECT deleted_at FROM manufacturer WHERE id=?",java.sql.Timestamp.class,source));
  assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='MANUFACTURER_MERGED' AND target_id=?",Integer.class,source));
 }
 @ParameterizedTest @CsvSource({"GLOBAL,PRODUCT","THIRD,PRODUCT","THIRD,ASSET"})
 void manufacturerPrivateSurvivorRejectsEachForeignDependentIndependently(String ownerScope,String dependent)throws Exception{
  var k=CatalogLabel.Kind.MANUFACTURER;UUID source=create(k,null,"Global source"),target=create(k,other,"Private survivor");
  UUID dependentTenant=ownerScope.equals("GLOBAL")?null:tenant,productId=null,assetId=null;
  if(dependent.equals("PRODUCT"))productId=catalog.createProduct(dependentTenant,category,source,"Foreign dependent",null,actor);
  else assetId=assets.register(tenant,new AssetService.Registration(new AssetService.DirectSelection(category,source),location,null,true,null,null,null),actor).id();
  var body=Map.of("sourceId",source,"targetId",target);long audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class),beforeEvents=assetId==null?0:events(assetId);
  var preview=data(request(admin,post(root(k)+"/merge-preview"),body));
  assertEquals(List.of("Target is not visible to every retained dependent"),preview.get("blockers"));
  assertEquals(dependent.equals("PRODUCT")?1L:0L,((Number)preview.get("productCount")).longValue());
  assertEquals(dependent.equals("ASSET")?1L:0L,((Number)preview.get("assetCount")).longValue());
  assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  var rejected=request(admin,post(root(k)+"/merge"),body);
  assertEquals(409,status(rejected));assertEquals("MERGE_REQUIRES_GLOBAL_TARGET",data(rejected).get("code"));
  if(productId!=null)assertEquals(source,jdbc.queryForObject("SELECT manufacturer_id FROM product WHERE id=?",UUID.class,productId));
  if(assetId!=null){assertEquals(source,jdbc.queryForObject("SELECT manufacturer_id FROM asset WHERE id=?",UUID.class,assetId));assertEquals(beforeEvents,events(assetId));}
  assertNull(jdbc.queryForObject("SELECT deleted_at FROM manufacturer WHERE id=?",java.sql.Timestamp.class,source));
  assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='MANUFACTURER_MERGED' AND target_id=?",Integer.class,source));
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void mergeRequiresPromotionForRetainedForeignAndGlobalProducts(CatalogLabel.Kind k)throws Exception{
  String column=k==CatalogLabel.Kind.CATEGORY?"category_id":"manufacturer_id";
  UUID source=create(k,null,"Global source"),target=create(k,tenant,"Private survivor");
  UUID global=catalog.createProduct(null,k==CatalogLabel.Kind.CATEGORY?source:category,k==CatalogLabel.Kind.MANUFACTURER?source:manufacturer,"Global dependent",null,actor);
  UUID foreign=catalog.createProduct(other,k==CatalogLabel.Kind.CATEGORY?source:category,k==CatalogLabel.Kind.MANUFACTURER?source:manufacturer,"Foreign dependent",null,actor);
  catalog.delete(CatalogService.Kind.PRODUCT,foreign,actor);
  var body=Map.of("sourceId",source.toString(),"targetId",target.toString());
  long audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
  var rejected=request(admin,post(root(k)+"/merge"),body);
  assertEquals(409,status(rejected));assertEquals("MERGE_REQUIRES_GLOBAL_TARGET",data(rejected).get("code"));
  assertEquals(source,jdbc.queryForObject("SELECT "+column+" FROM product WHERE id=?",UUID.class,global));
  assertEquals(source,jdbc.queryForObject("SELECT "+column+" FROM product WHERE id=?",UUID.class,foreign));
  assertNull(jdbc.queryForObject("SELECT deleted_at FROM "+k.name().toLowerCase()+" WHERE id=?",java.sql.Timestamp.class,source));
  assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  assertEquals(204,status(request(admin,post(root(k)+"/"+target+"/publish"),Map.of())));
  assertEquals(target,jdbc.queryForObject("SELECT id FROM "+k.name().toLowerCase()+" WHERE id=? AND visibility='GLOBAL' AND tenant_id IS NULL",UUID.class,target));
  assertEquals(204,status(request(admin,post(root(k)+"/merge"),body)));
  assertEquals(target,jdbc.queryForObject("SELECT "+column+" FROM product WHERE id=?",UUID.class,global));
  assertEquals(target,jdbc.queryForObject("SELECT "+column+" FROM product WHERE id=?",UUID.class,foreign));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action=? AND target_id=?",Integer.class,k+"_MERGED",source));
  UUID retainedSource=create(k,null,"Retained source"),retainedTarget=create(k,tenant,"Retained survivor");
  UUID retained=catalog.createProduct(other,k==CatalogLabel.Kind.CATEGORY?retainedSource:category,k==CatalogLabel.Kind.MANUFACTURER?retainedSource:manufacturer,"Deleted foreign dependent",null,actor);
  catalog.delete(CatalogService.Kind.PRODUCT,retained,actor);
  var retainedBody=Map.of("sourceId",retainedSource.toString(),"targetId",retainedTarget.toString());
  var retainedRejection=request(admin,post(root(k)+"/merge"),retainedBody);
  assertEquals(409,status(retainedRejection));assertEquals("MERGE_REQUIRES_GLOBAL_TARGET",data(retainedRejection).get("code"));
  assertEquals(retainedSource,jdbc.queryForObject("SELECT "+column+" FROM product WHERE id=?",UUID.class,retained));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action=? AND target_id=?",Integer.class,k+"_MERGED",retainedSource));
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void mergeRequiresPromotionForRetainedForeignAsset(CatalogLabel.Kind k)throws Exception{
  String column=k==CatalogLabel.Kind.CATEGORY?"category_id":"manufacturer_id";
  UUID source=create(k,null,"Global asset source"),target=create(k,tenant,"Private asset survivor");
  UUID foreignLocation=places.create(PlaceService.Kind.LOCATION,other,"Foreign room",actor);
  UUID foreignOwner=places.create(PlaceService.Kind.OWNER,other,"Foreign owner",actor);
  var a=assets.register(other,new AssetService.Registration(new AssetService.DirectSelection(k==CatalogLabel.Kind.CATEGORY?source:category,k==CatalogLabel.Kind.MANUFACTURER?source:manufacturer),foreignLocation,foreignOwner,true,null,null,null),actor);
  assets.lifecycle(other,a.id(),0,LifecycleState.DEREGISTERED,DeregistrationReason.ERROR,null,actor);
  assets.delete(other,a.id(),1,actor);
  long audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class),before=events(a.id());
  var body=Map.of("sourceId",source.toString(),"targetId",target.toString());
  var rejected=request(admin,post(root(k)+"/merge"),body);
  assertEquals(409,status(rejected));assertEquals("MERGE_REQUIRES_GLOBAL_TARGET",data(rejected).get("code"));
  assertEquals(source,jdbc.queryForObject("SELECT "+column+" FROM asset WHERE id=?",UUID.class,a.id()));
  assertEquals(before,events(a.id()));assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  assertNull(jdbc.queryForObject("SELECT deleted_at FROM "+k.name().toLowerCase()+" WHERE id=?",java.sql.Timestamp.class,source));
  assertEquals(204,status(request(admin,post(root(k)+"/"+target+"/publish"),Map.of())));
  assertEquals(204,status(request(admin,post(root(k)+"/merge"),body)));
  assertEquals(target,jdbc.queryForObject("SELECT "+column+" FROM asset WHERE id=?",UUID.class,a.id()));
  assertEquals(before+1,events(a.id()));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action=? AND target_id=?",Integer.class,k+"_MERGED",source));
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void mutationAuditFailureRollsBack(CatalogLabel.Kind k)throws Exception{
  UUID id=create(k,null,"Before");var before=data(request(admin,get(root(k)+"/"+id),null));
  jdbc.execute("CREATE FUNCTION step2_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='"+k+"_EDITED' THEN RAISE EXCEPTION 'isolated audit failure' USING ERRCODE='23514'; END IF; RETURN NEW; END; $$");jdbc.execute("CREATE TRIGGER step2_reject_audit BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION step2_reject_audit()");
  try{assertEquals(409,status(request(admin,put(root(k)+"/"+id),Map.of("name","Rolled back"))));assertEquals(before,data(request(admin,get(root(k)+"/"+id),null)));}
  finally{jdbc.execute("DROP TRIGGER step2_reject_audit ON audit_log");jdbc.execute("DROP FUNCTION step2_reject_audit()");}
 }
 @ParameterizedTest @EnumSource(CatalogLabel.Kind.class) void mergeFailureRollsBackReferencesMetadataAndHistory(CatalogLabel.Kind k)throws Exception{
  for(String failingTable:List.of("audit_log","asset_event")){
   UUID source=create(k,null,"Rollback source"),target=create(k,null,"Rollback target");
   UUID dependent=catalog.createProduct(null,k==CatalogLabel.Kind.CATEGORY?source:category,k==CatalogLabel.Kind.MANUFACTURER?source:manufacturer,"Rollback dependent",null,actor);
   var a=assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(dependent),location,owner,true,null,null,null),actor);
   var beforeSource=data(request(admin,get(root(k)+"/"+source),null));var beforeProduct=catalog.read(CatalogService.Kind.PRODUCT,dependent);var beforeAsset=assets.get(tenant,a.id());
   long audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class),history=events(a.id());clock.now=clock.now.plusSeconds(1);
   String predicate=failingTable.equals("audit_log")?"NEW.action='"+k+"_MERGED'":"NEW.asset_id='"+a.id()+"'::uuid AND NEW.type='UPDATED'";
   jdbc.execute("CREATE FUNCTION step2_reject_merge() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF "+predicate+" THEN RAISE EXCEPTION 'isolated history failure' USING ERRCODE='23514'; END IF; RETURN NEW; END; $$");
   jdbc.execute("CREATE TRIGGER step2_reject_merge BEFORE INSERT ON "+failingTable+" FOR EACH ROW EXECUTE FUNCTION step2_reject_merge()");
   try{
    assertEquals(409,status(request(admin,post(root(k)+"/merge"),Map.of("sourceId",source.toString(),"targetId",target.toString()))));
    assertEquals(beforeSource,data(request(admin,get(root(k)+"/"+source),null)));assertEquals(beforeProduct,catalog.read(CatalogService.Kind.PRODUCT,dependent));assertEquals(beforeAsset,assets.get(tenant,a.id()));
    assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));assertEquals(history,events(a.id()));
   }finally{jdbc.execute("DROP TRIGGER step2_reject_merge ON "+failingTable);jdbc.execute("DROP FUNCTION step2_reject_merge()");}
  }
 }

}
