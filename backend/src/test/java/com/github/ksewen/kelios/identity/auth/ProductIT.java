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
class ProductIT {
 static class MutableClock extends Clock {
  Instant now=Instant.parse("2026-10-11T12:00:00Z");
  public Instant instant(){return now;} public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
 }
 @org.springframework.boot.test.context.TestConfiguration static class Time {
  @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary MutableClock labelClock(){return new MutableClock();}
 }
 @Autowired MutableClock clock; @Autowired ProductOperations products;
 @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17-alpine");
 static final AuthTestKeys KEYS;static{try{KEYS=new AuthTestKeys("product-keys");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
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

 static final String ROOT="/api/v1/catalog/products";
 String contextual(UUID t){return "/api/v1/tenants/"+t+"/catalog/products";}
 Map<String,Object> command(String name,UUID c,UUID m,Object number){var b=new LinkedHashMap<String,Object>();b.put("name",name);b.put("categoryId",c.toString());b.put("manufacturerId",m.toString());b.put("manufacturerNumber",number);return b;}
 UUID create(UUID t,String name,String number){return catalog.createProduct(t,category,manufacturer,name,number,actor);}
 String prefix(){return "product-"+UUID.randomUUID()+"-";}
 @SuppressWarnings("unchecked") List<Map<String,Object>> items(MvcResult r){return (List<Map<String,Object>>)data(r).get("items");}
 long total(MvcResult r){return ((Number)data(r).get("totalElements")).longValue();}
 MvcResult list(UUID t,Map<String,String> query)throws Exception{var b=get(t==null?ROOT:contextual(t));query.forEach(b::param);var r=request(admin,b,null);assertEquals(200,status(r),r.getResponse().getContentAsString());return r;}
 @Test void defaultPagingOrderingAndAllSizes()throws Exception{
  String p=prefix();UUID older=create(null,p+"Z",null);clock.now=clock.now.plusSeconds(1);UUID newer=create(null,p+"A",null);
  var r=list(null,Map.of("q",p));assertEquals(0,data(r).get("page"));assertEquals(20,data(r).get("size"));assertEquals(newer.toString(),items(r).getFirst().get("id"));assertEquals(older.toString(),items(r).getLast().get("id"));
  for(int i=0;i<103;i++)create(null,p+i,null);
  for(int size:List.of(1,10,20,30,40,50,100)){r=list(null,Map.of("q",p,"size",String.valueOf(size)));assertEquals(size,items(r).size());assertEquals(size,data(r).get("size"));assertEquals(105,total(r));}
  r=list(null,Map.of("q",p,"page","2147483647","size","100"));assertEquals(105,total(r));assertTrue(items(r).isEmpty());assertEquals(2147483647,data(r).get("page"));
  assertEquals(0,total(list(null,Map.of("q",prefix()))));assertEquals(25,ListInput.size(Map.of()));
 }
 static java.util.stream.Stream<Arguments> invalidQueries(){return java.util.stream.Stream.of(
  new String[]{"page","-1"},new String[]{"page","2147483648"},new String[]{"page","1.5"},new String[]{"size","0"},new String[]{"size","101"},new String[]{"size","bad"},new String[]{"sort","createdAt"},new String[]{"sort","category.name"},new String[]{"sort","manufacturer.name"},new String[]{"sort","id"},new String[]{"sort","name;DROP TABLE product"},new String[]{"direction","up"},new String[]{"direction","desc"},new String[]{"visibility","PUBLIC"},new String[]{"categoryId","1-1-1-1-1"},new String[]{"manufacturerId","invalid"},new String[]{"deleted","false"},new String[]{"deleted","true"},new String[]{"deleted","all"},new String[]{"unpaged","true"},new String[]{"tenantId",UUID.randomUUID().toString()}).map(v->Arguments.of(v[0],v[1]));}
 @ParameterizedTest @MethodSource("invalidQueries") void strictQuery(String key,String value)throws Exception{var r=request(admin,get(ROOT).param(key,value),null);assertEquals(400,status(r));assertEquals("INVALID_INPUT",data(r).get("code"));assertTrue(r.getResponse().getContentType().startsWith("application/problem+json"));}
 @Test void nameAndNumberLiteralSearchNormalization()throws Exception{
  String p=prefix();create(null,"Ordinary",p+"%_\\Code");create(null,p+"%_\\Label",null);create(null,"Ordinary",p+"XYCode");
  assertEquals(2,total(list(null,Map.of("q","  "+p.toUpperCase()+"%_\\  "))));
  assertEquals(1,total(list(null,Map.of("manufacturerNumber"," "+p+"%_\\ "))));
  assertEquals(total(list(null,Map.of())),total(list(null,Map.of("q"," ".repeat(200),"manufacturerNumber","  "))));
  create(null,"Long","x".repeat(128));assertEquals(1,total(list(null,Map.of("q"," "+"x".repeat(128)+" "))));
  for(String field:List.of("q","manufacturerNumber"))assertEquals(400,status(request(admin,get(ROOT).param(field,"x".repeat(129)),null)));
 }
 @ParameterizedTest @ValueSource(strings={"name","visibility","manufacturerNumber"}) void serverSortingAcrossDuplicatePages(String field)throws Exception{
  String p=prefix();var expected=new HashSet<String>();for(int i=0;i<67;i++)expected.add(create(i%2==0?null:tenant,p+(char)('A'+i%3),i%10==0?null:"Part"+i%3).toString());
  for(String direction:List.of("asc","desc")){
   String previous=null;boolean nullSeen=false;var seen=new HashSet<String>();
   for(int page=0;page<4;page++)for(var row:items(list(tenant,Map.of("q",p,"sort",field,"direction",direction,"page",String.valueOf(page),"size","20")))){
    assertTrue(seen.add((String)row.get("id")));String value=(String)row.get(field);
    if(value==null){nullSeen=true;continue;}assertFalse(nullSeen,"NULL values last in either direction");
    if(previous!=null)assertTrue(direction.equals("asc")?previous.compareTo(value)<=0:previous.compareTo(value)>=0);previous=value;
   }
   assertEquals(expected,seen);
  }
  assertEquals(items(list(tenant,Map.of("q",p,"sort",field,"direction","asc"))),items(list(tenant,Map.of("q",p,"sort",field))));
 }
 @Test void combinedFiltersScopedCountsAndDeletedExclusion()throws Exception{
  String p=prefix();UUID c=catalog.createCategory(tenant,"Own C",actor),m=catalog.createManufacturer(tenant,"Own M",actor);
  for(int i=0;i<5;i++)catalog.createProduct(tenant,c,m,p+i,"HP-42",actor);
  create(null,p+"Global","HP-42");create(other,p+"Foreign","HP-42");create(tenant,p+"Different refs","HP-42");
  UUID gone=catalog.createProduct(tenant,c,m,p+"Deleted","HP-42",actor);catalog.delete(CatalogService.Kind.PRODUCT,gone,actor);
  var q=Map.of("q",p,"visibility","TENANT","categoryId",c.toString(),"manufacturerId",m.toString(),"manufacturerNumber","hp","sort","name","direction","desc","page","1","size","2");
  var r=list(tenant,q);assertEquals(5,total(r));assertEquals(2,items(r).size());assertTrue(items(r).stream().allMatch(x->x.get("categoryId").equals(c.toString())&&x.get("manufacturerId").equals(m.toString())));
  assertEquals(5,total(list(null,q)));assertEquals(0,total(list(tenant,Map.of("categoryId",UUID.randomUUID().toString()))));
  UUID foreignC=catalog.createCategory(other,"Foreign",actor);catalog.createProduct(other,foreignC,manufacturer,p+"Foreign ref",null,actor);
  assertEquals(0,total(list(tenant,Map.of("categoryId",foreignC.toString()))));
 }
 @Test void rootAggregatesGlobalAndTenantProductsBeforeFilteringSortingAndPaging()throws Exception{
  String p=prefix();var ids=new HashSet<String>();
  for(int i=0;i<24;i++){
   ids.add(create(null,p+"Global "+i,"Part-"+i).toString());
   ids.add(create(tenant,p+"North "+i,"Part-"+i).toString());
   ids.add(create(other,p+"South "+i,"Part-"+i).toString());
  }
  UUID deleted=create(other,p+"Deleted",null);catalog.delete(CatalogService.Kind.PRODUCT,deleted,actor);
  assertEquals(72,total(list(null,Map.of("q",p))));
  assertEquals(24,total(list(null,Map.of("q",p+"South"))));
  assertEquals(48,total(list(null,Map.of("q",p,"visibility","TENANT"))));
  assertEquals(24,total(list(null,Map.of("q",p,"visibility","GLOBAL"))));
  for(String direction:List.of("asc","desc")){
   var seen=new HashSet<String>();String previous=null;
   for(int page=0;page<4;page++)for(var item:items(list(null,Map.of("q",p,"sort","name","direction",direction,"page",String.valueOf(page))))){
    String name=(String)item.get("name");if(previous!=null)assertTrue(direction.equals("asc")?previous.compareTo(name)<=0:previous.compareTo(name)>=0);
    previous=name;assertTrue(seen.add((String)item.get("id")));
   }
   assertEquals(ids,seen);
  }
  assertEquals(48,total(list(tenant,Map.of("q",p))));
  var own=principal(Scope.TENANT,Role.USER,Status.ACTIVE);
  var result=request(own,get(ROOT).param("q",p),null);assertEquals(200,status(result));assertEquals(48,total(result));
  assertTrue(items(result).stream().noneMatch(item->other.toString().equals(item.get("tenantId"))));
 }
 @Test void authoritativeTenantAndIndependentDetail()throws Exception{
  UUID own=create(tenant,"Own","number"),foreign=create(other,"Foreign",null);var local=principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE);
  for(UUID supplied:List.of(tenant,other,UUID.randomUUID())){
   var r=request(local,get(contextual(supplied)).param("q","Own"),null);assertEquals(200,status(r));assertEquals(1,total(r));
   r=request(local,post(contextual(supplied)),command("Created",category,manufacturer,null));assertEquals(201,status(r));assertEquals(tenant,catalog.read(CatalogService.Kind.PRODUCT,UUID.fromString((String)data(r).get("id"))).tenantId());
  }
  var made=request(local,post(ROOT),command("Own root",category,manufacturer,null));assertEquals(201,status(made));assertEquals(tenant,catalog.read(CatalogService.Kind.PRODUCT,UUID.fromString((String)data(made).get("id"))).tenantId());
  assertEquals(404,status(request(local,get(ROOT+"/"+foreign),null)));assertEquals(404,status(request(local,get(ROOT+"/"+UUID.randomUUID()),null)));
  assertEquals(404,status(request(local,put(ROOT+"/"+foreign),command("Attack",category,manufacturer,null))));assertEquals(403,status(request(local,put(ROOT+"/"+product),command("Attack",category,manufacturer,null))));
  long audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);var d=data(request(local,get(ROOT+"/"+own),null));
  assertEquals(category.toString(),((Map<?,?>)d.get("category")).get("id"));assertEquals("Category",((Map<?,?>)d.get("category")).get("name"));assertEquals("Manufacturer",((Map<?,?>)d.get("manufacturer")).get("name"));assertEquals(tenant.toString(),((Map<?,?>)d.get("tenant")).get("id"));
  assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));assertFalse(d.containsKey("passwordHash"));assertFalse(items(list(tenant,Map.of("q","Own"))).getFirst().containsKey("deletedAt"));
  catalog.delete(CatalogService.Kind.PRODUCT,own,actor);assertEquals(true,data(request(local,get(ROOT+"/"+own),null)).get("deleted"));
 }
 @Test void strictDtoAndMetadataOwnership()throws Exception{
  for(String field:List.of("createdBy","createdAt","updatedBy","updatedAt","tenantId","visibility","deletedAt","deletedBy","category","manufacturer","actor")){
   var body=command("Overposting",category,manufacturer,null);body.put(field,"forged");assertEquals(400,status(request(admin,post(ROOT),body)));assertEquals(400,status(request(admin,put(ROOT+"/"+product),body)));
  }
  for(Object bad:List.of(1,true,List.of("text"))){assertEquals(400,status(request(admin,post(ROOT),command("Name",category,manufacturer,bad))));var body=command("Name",category,manufacturer,null);body.put("name",bad);assertEquals(400,status(request(admin,post(ROOT),body)));}
  var body=command("Name",category,manufacturer,null);body.put("categoryId","1-1-1-1-1");assertEquals(400,status(request(admin,post(ROOT),body)));
  var r=request(admin,post(contextual(tenant)),command(" Before ",category,manufacturer,"   "));assertEquals(201,status(r));UUID id=UUID.fromString((String)data(r).get("id"));assertEquals(ROOT+"/"+id,r.getResponse().getHeader("Location"));
  var before=(Map<?,?>)data(request(admin,get(ROOT+"/"+id),null)).get("metadata");clock.now=clock.now.plusSeconds(5);var editor=principal(Scope.GLOBAL,Role.ADMIN,Status.ACTIVE);
  assertEquals(204,status(request(editor,put(ROOT+"/"+id),command(" After ",category,manufacturer," Part "))));var after=data(request(admin,get(ROOT+"/"+id),null));var metadata=(Map<?,?>)after.get("metadata");
  assertEquals(before.get("createdBy"),metadata.get("createdBy"));assertEquals(before.get("createdAt"),metadata.get("createdAt"));assertEquals(editor.id().toString(),metadata.get("updatedBy"));assertNotEquals(before.get("updatedAt"),metadata.get("updatedAt"));assertEquals("Part",after.get("manufacturerNumber"));
 }
 @Test void referenceVisibilityAndNewAssignmentGuards()throws Exception{
  UUID ownC=catalog.createCategory(tenant,"Own category",actor),foreignC=catalog.createCategory(other,"Foreign category",actor),deletedM=catalog.createManufacturer(null,"Deleted",actor);catalog.delete(CatalogService.Kind.MANUFACTURER,deletedM,actor);
  assertEquals(409,status(request(admin,post(ROOT),command("Invalid global",ownC,manufacturer,null))));
  assertEquals(404,status(request(admin,post(contextual(tenant)),command("Foreign",foreignC,manufacturer,null))));
  assertEquals(409,status(request(admin,post(ROOT),command("Deleted",category,deletedM,null))));
  var r=request(admin,post(contextual(tenant)),command("Own refs",ownC,manufacturer,null));assertEquals(201,status(r));UUID id=UUID.fromString((String)data(r).get("id"));
  assertEquals(409,status(request(admin,post(ROOT+"/"+id+"/publish"),Map.of())));assertEquals(404,status(request(admin,put(ROOT+"/"+id),command("Foreign",foreignC,manufacturer,null))));
  var d=data(request(admin,get(ROOT+"/"+id),null));assertEquals(ownC.toString(),((Map<?,?>)d.get("category")).get("id"));
 }
 @Test void historicalUsePermanentlyFreezesEditButNoCurrentReferencesAllowsDeletion()throws Exception{
  var a=asset();long history=events(a.id());
  assertEquals("PRODUCT_EDIT_FROZEN",data(request(admin,put(ROOT+"/"+product),command("Changed",category,manufacturer,null))).get("code"));assertEquals("REFERENCED",data(request(admin,delete(ROOT+"/"+product),null)).get("code"));assertEquals(history,events(a.id()));
  assets.changeCatalog(tenant,a.id(),0,new AssetService.DirectSelection(category,manufacturer),actor);
  assertEquals("PRODUCT_EDIT_FROZEN",data(request(admin,put(ROOT+"/"+product),command("Still frozen",category,manufacturer,null))).get("code"));
  assertEquals(204,status(request(admin,delete(ROOT+"/"+product),null)));assertEquals(2,events(a.id()));assertEquals(true,data(request(admin,get(ROOT+"/"+product),null)).get("deleted"));
 }
 @Test void deregisteredAndSoftDeletedRetainedAssetsStillBlockDeletion()throws Exception{
  var a=asset();assets.lifecycle(tenant,a.id(),0,LifecycleState.DEREGISTERED,DeregistrationReason.ERROR,null,actor);assets.delete(tenant,a.id(),1,actor);
  assertEquals("REFERENCED",data(request(admin,delete(ROOT+"/"+product),null)).get("code"));assertEquals("PRODUCT_EDIT_FROZEN",data(request(admin,put(ROOT+"/"+product),command("Blocked",category,manufacturer,null))).get("code"));
 }
 @Test void publicationIsSeparateFromFrozenOrdinaryEditing()throws Exception{
  UUID id=create(tenant,"Frozen publish",null);var a=assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(id),location,owner,true,null,null,null),actor);
  assertEquals(204,status(request(admin,post(ROOT+"/"+id+"/publish"),Map.of())));assertEquals(tenant,attribution("PRODUCT_PUBLISHED",id));assertEquals("GLOBAL",data(request(admin,get(ROOT+"/"+id),null)).get("visibility"));assertEquals(1,events(a.id()));
  assertEquals("PRODUCT_EDIT_FROZEN",data(request(admin,put(ROOT+"/"+id),command("Frozen",category,manufacturer,null))).get("code"));
 }
 @Test void combinedSavePublishesAtomicallyAndPublishOnlySkipsFrozenEdit()throws Exception{
  UUID combined=create(tenant,"Before combined",null);var edit=command("After combined",category,manufacturer,"P-1");edit.put("publishGlobally",true);
  assertEquals(204,status(request(admin,put(ROOT+"/"+combined),edit)));
  var result=data(request(admin,get(ROOT+"/"+combined),null));assertEquals("After combined",result.get("name"));assertEquals("GLOBAL",result.get("visibility"));
  assertEquals(tenant,attribution("PRODUCT_EDITED",combined));assertEquals(tenant,attribution("PRODUCT_PUBLISHED",combined));
  UUID frozen=create(tenant,"Frozen publish only",null);var asset=assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(frozen),location,owner,true,null,null,null),actor);
  var publishOnly=command("Frozen publish only",category,manufacturer,null);publishOnly.put("publishGlobally",true);
  assertEquals(204,status(request(admin,put(ROOT+"/"+frozen),publishOnly)));
  assertEquals("GLOBAL",data(request(admin,get(ROOT+"/"+frozen),null)).get("visibility"));assertEquals(1,events(asset.id()));
  assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='PRODUCT_EDITED' AND target_id=?",Long.class,frozen));
  assertEquals(tenant,attribution("PRODUCT_PUBLISHED",frozen));
  UUID localCategory=catalog.createCategory(tenant,"Local category",actor),blocked=catalog.createProduct(tenant,localCategory,manufacturer,"Before rollback",null,actor);
  var invalid=command("Changed but invalid Global references",localCategory,manufacturer,null);invalid.put("publishGlobally",true);
  assertEquals(409,status(request(admin,put(ROOT+"/"+blocked),invalid)));
  result=data(request(admin,get(ROOT+"/"+blocked),null));assertEquals("Before rollback",result.get("name"));assertEquals("TENANT",result.get("visibility"));
  assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action IN ('PRODUCT_EDITED','PRODUCT_PUBLISHED') AND target_id=?",Long.class,blocked));
  assertEquals(403,status(request(principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE),put(ROOT+"/"+create(tenant,"Own",null)),publishOnly)));
  var bad=command("Strict",category,manufacturer,null);bad.put("publishGlobally","true");assertEquals(400,status(request(admin,put(ROOT+"/"+create(tenant,"Strict",null)),bad)));
 }
 @Test void authorizationAndNoMerge()throws Exception{
  for(Scope scope:Scope.values())for(Role role:Role.values()){
   var who=principal(scope,role,Status.ACTIVE);assertEquals(200,status(request(who,get(ROOT+"/"+product),null)));assertEquals(200,status(request(who,get(contextual(tenant)),null)));
   assertEquals(role==Role.ADMIN?201:403,status(request(who,post(contextual(tenant)),command("Create",category,manufacturer,null))));
   assertEquals(scope==Scope.GLOBAL&&role==Role.ADMIN?204:403,status(request(who,post(ROOT+"/"+create(tenant,"Publish",null)+"/publish"),Map.of())));
   var pending=principal(scope,role,Status.PENDING);assertEquals(403,status(request(pending,get(ROOT),null)));assertEquals(403,status(request(pending,get(ROOT+"/"+product),null)));assertEquals(403,status(request(pending,post(ROOT),command("Blocked",category,manufacturer,null))));
  }
  var disabled=principal(Scope.GLOBAL,Role.ADMIN,Status.DISABLED);assertThrows(AuthFailure.class,()->tokens.login(disabled));
  org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(disabled,null,List.of()));
  try{assertThrows(AuthFailure.class,()->products.detail(product));assertThrows(AuthFailure.class,()->products.create(null,new ProductModel.Command("Blocked",category,manufacturer,null)));}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
  long before=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class),count=jdbc.queryForObject("SELECT count(*) FROM product",Long.class);
  for(String route:List.of("merge","merge-preview"))assertEquals(404,status(request(admin,post(ROOT+"/"+route),Map.of("sourceId",product.toString(),"targetId",product.toString()))));
  assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));assertEquals(count,jdbc.queryForObject("SELECT count(*) FROM product",Long.class));
 }
 @ParameterizedTest @ValueSource(strings={"create","edit","publish","combined","delete"}) void auditFailureRollsBackMetadataAndMutation(String operation)throws Exception{
  UUID id=create(tenant,"Before",null);var before=data(request(admin,get(ROOT+"/"+id),null));long count=jdbc.queryForObject("SELECT count(*) FROM product",Long.class),audits=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
  String action=switch(operation){case "create"->"PRODUCT_CREATED";case "edit"->"PRODUCT_EDITED";case "publish","combined"->"PRODUCT_PUBLISHED";default->"PRODUCT_DELETED";};
  jdbc.execute("CREATE FUNCTION step3_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='"+action+"' THEN RAISE EXCEPTION 'isolated failure' USING ERRCODE='23514'; END IF; RETURN NEW; END; $$");jdbc.execute("CREATE TRIGGER step3_reject_audit BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION step3_reject_audit()");
  clock.now=clock.now.plusSeconds(10);
  try{
   var r=switch(operation){case "create"->request(admin,post(contextual(tenant)),command("Rollback",category,manufacturer,"N"));case "edit"->request(admin,put(ROOT+"/"+id),command("Rollback",category,manufacturer,"N"));case "publish"->request(admin,post(ROOT+"/"+id+"/publish"),Map.of());case "combined"->{var body=command("Rollback",category,manufacturer,"N");body.put("publishGlobally",true);yield request(admin,put(ROOT+"/"+id),body);}default->request(admin,delete(ROOT+"/"+id),null);};
   assertEquals(409,status(r));assertEquals(before,data(request(admin,get(ROOT+"/"+id),null)));assertEquals(count,jdbc.queryForObject("SELECT count(*) FROM product",Long.class));assertEquals(audits,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
  }finally{jdbc.execute("DROP TRIGGER step3_reject_audit ON audit_log");jdbc.execute("DROP FUNCTION step3_reject_audit()");}
 }
 @Test void associationLabelsAreReadableButNotQueryOrCommandIdentity()throws Exception{
  String label=prefix();UUID c1=catalog.createCategory(null,label,actor),c2=catalog.createCategory(null,label,actor),m1=catalog.createManufacturer(null,label,actor),m2=catalog.createManufacturer(null,label,actor);
  UUID p1=catalog.createProduct(null,c1,m1,"Duplicate Product",null,actor),p2=catalog.createProduct(null,c2,m2,"Duplicate Product",null,actor);
  assertEquals(0,total(list(null,Map.of("q",label))));
  for(UUID id:List.of(p1,p2)){var d=data(request(admin,get(ROOT+"/"+id),null));assertEquals(label,((Map<?,?>)d.get("category")).get("name"));assertEquals(label,((Map<?,?>)d.get("manufacturer")).get("name"));}
  assertEquals(p1.toString(),items(list(null,Map.of("categoryId",c1.toString(),"manufacturerId",m1.toString()))).getFirst().get("id"));
  assertEquals(0,total(list(null,Map.of("categoryId",c1.toString(),"manufacturerId",m2.toString()))));
 }
 @ParameterizedTest @ValueSource(strings={"category","manufacturer"}) void foreignAndDeletedReferencesCannotBeAssigned(String reference)throws Exception{
  boolean isCategory=reference.equals("category");UUID foreign=isCategory?catalog.createCategory(other,"Foreign reference",actor):catalog.createManufacturer(other,"Foreign reference",actor);
  UUID own=create(tenant,"Own edit",null);var body=command("Foreign assignment",isCategory?foreign:category,isCategory?manufacturer:foreign,null);
  assertEquals(404,status(request(admin,post(contextual(tenant)),body)));assertEquals(404,status(request(admin,put(ROOT+"/"+own),body)));
  UUID deleted=isCategory?catalog.createCategory(null,"Deleted reference",actor):catalog.createManufacturer(null,"Deleted reference",actor);catalog.delete(isCategory?CatalogService.Kind.CATEGORY:CatalogService.Kind.MANUFACTURER,deleted,actor);
  body=command("Deleted assignment",isCategory?deleted:category,isCategory?manufacturer:deleted,null);
  assertEquals(409,status(request(admin,post(contextual(tenant)),body)));assertEquals(409,status(request(admin,put(ROOT+"/"+own),body)));
 }

 @ParameterizedTest @CsvSource({"CATEGORY,edit","MANUFACTURER,edit","CATEGORY,publish","MANUFACTURER,publish","CATEGORY,register","MANUFACTURER,register"})
 void productReferenceRaceHasResourceCorrectHttpConflict(String resource,String operation)throws Exception {
  var kind=CatalogService.MergeKind.valueOf(resource);boolean categoryRace=kind==CatalogService.MergeKind.CATEGORY;
  UUID source=categoryRace?category:manufacturer,target=categoryRace?catalog.createCategory(null,"Race target",actor):catalog.createManufacturer(null,"Race target",actor);
  UUID subject=create(operation.equals("publish")?tenant:null,"Race Product","Original number");
  long assetCount=jdbc.queryForObject("SELECT count(*) FROM asset",Long.class),eventCount=jdbc.queryForObject("SELECT count(*) FROM asset_event",Long.class);
  var held=new CountDownLatch(1);var release=new CountDownLatch(1);var mergePid=new CompletableFuture<Integer>();
  clock.now=clock.now.plusSeconds(10);
  try(var pool=Executors.newFixedThreadPool(2)) {
   var merge=pool.submit(()->new TransactionTemplate(tm).execute(transaction->{
    jdbc.execute("SET LOCAL statement_timeout='15s'");mergePid.complete(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
    catalog.lockMerge(kind,source,target);held.countDown();
    try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new RuntimeException(ex);}
    merges.merge(kind,source,target,actor);return jdbc.queryForMap("SELECT * FROM product WHERE id=?",subject);
   }));
   try {
    assertTrue(held.await(10,TimeUnit.SECONDS));int pid=mergePid.get(10,TimeUnit.SECONDS);
    var command=pool.submit(()->switch(operation){
     case "edit"->request(admin,put(ROOT+"/"+subject),command("Must not commit",category,manufacturer,"Must not commit"));
     case "publish"->request(admin,post(ROOT+"/"+subject+"/publish"),Map.of());
     default->request(admin,post("/api/v1/tenants/"+tenant+"/assets"),Map.of("productId",subject,"locationId",location,"loanable",true));
    });
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);List<String> blocked=List.of();
    while(System.nanoTime()<deadline){blocked=jdbc.queryForList("SELECT query FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",String.class,pid);if(!blocked.isEmpty())break;Thread.sleep(10);}
    assertFalse(blocked.isEmpty(),"HTTP command must actually wait behind Merge's label lock");assertTrue(blocked.stream().anyMatch(query->query.contains("FOR SHARE")));
    release.countDown();var committed=merge.get(20,TimeUnit.SECONDS);var response=command.get(20,TimeUnit.SECONDS);
    assertEquals(409,status(response),response.getResponse().getContentAsString());assertEquals("CONCURRENT_OPERATION",data(response).get("code"));
    assertEquals(operation.equals("register")?"Product dependencies changed during reference validation; review before resubmitting":"Concurrent operation; review current state before resubmitting",data(response).get("detail"));
    assertFalse(response.getResponse().getContentAsString().contains("STALE_ASSET_VERSION"));assertFalse(response.getResponse().getContentAsString().contains("Asset changed"));
    assertTrue(response.getResponse().getContentType().startsWith("application/problem+json"));assertEquals("no-store",response.getResponse().getHeader("Cache-Control"));
    assertEquals(committed,jdbc.queryForMap("SELECT * FROM product WHERE id=?",subject),"Failed command leaves exactly the committed Merge state/metadata");
    assertEquals(target,committed.get(categoryRace?"category_id":"manufacturer_id"));assertEquals("Race Product",committed.get("name"));assertEquals(operation.equals("publish")?"TENANT":"GLOBAL",committed.get("visibility"));
    assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action IN ('PRODUCT_EDITED','PRODUCT_PUBLISHED')",Integer.class,subject));
    assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action=?",Integer.class,source,resource+"_MERGED"));
    assertEquals(assetCount,jdbc.queryForObject("SELECT count(*) FROM asset",Long.class));assertEquals(eventCount,jdbc.queryForObject("SELECT count(*) FROM asset_event",Long.class));
   }finally{release.countDown();}
  }
 }
 @Test void assetVersionConflictRemainsAssetSpecific()throws Exception {
  var a=asset();assertEquals(200,status(request(admin,put("/api/v1/assets/"+a.id()+"/metadata"),Map.of("expectedVersion",0,"note","Committed"))));
  var before=jdbc.queryForMap("SELECT * FROM asset WHERE id=?",a.id());long history=events(a.id());
  var stale=request(admin,put("/api/v1/assets/"+a.id()+"/metadata"),Map.of("expectedVersion",0,"note","Must not commit"));
  assertEquals(409,status(stale));assertEquals("STALE_ASSET_VERSION",data(stale).get("code"));assertEquals("Asset changed; review its current version before resubmitting",data(stale).get("detail"));
  assertEquals(before,jdbc.queryForMap("SELECT * FROM asset WHERE id=?",a.id()));assertEquals(history,events(a.id()));
 }

}
