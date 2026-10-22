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
 @Autowired AssetCsvImportRowWriter csvWriter;
 @Autowired TenantService tenants;@Autowired UserService users;@Autowired IdentityStore identities;@Autowired AuthenticationService auth;
 @Autowired OidcConfigurationService configs;@Autowired OidcProvisioning provisioning;@Autowired JwtTokens tokens;
 @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager tm;@Autowired WebApplicationContext context;
 final JsonMapper json=JsonMapper.builder().build();final Actor actor=new Actor(UUID.randomUUID(),"Global historical actor");
 MockMvc mvc;UUID tenant,other,category,manufacturer,product,location,owner;Identity admin;
 @BeforeEach void setup(){clock.now=Instant.parse("2026-10-11T12:00:00Z");tenant=tenants.create("Tenant "+UUID.randomUUID(),actor);other=tenants.create("Other",actor);category=catalog.createCategory(null,"Category",actor);manufacturer=catalog.createManufacturer(null,"Manufacturer",actor);product=catalog.createProduct(null,category,manufacturer,"Product",null,actor);location=places.create(PlaceService.Kind.LOCATION,tenant,"Room",actor);owner=places.create(PlaceService.Kind.OWNER,tenant,"Owner",actor);admin=new Identity(actor.id(),Scope.GLOBAL,null,Role.ADMIN,Status.ACTIVE,actor.displayName());mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 AssetService.View asset(){return assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(product),location,owner,true,null,null,null),actor);}
 MvcResult request(Identity who,MockHttpServletRequestBuilder b,Object body)throws Exception{if(who!=null)b.header("Authorization","Bearer "+tokens.login(who).accessToken());if(body!=null)b.contentType("application/json").content(json.writeValueAsString(body));return mvc.perform(b).andReturn();}
 MvcResult request(Identity who,org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder b,Object body)throws Exception{
  if(body!=null)throw new IllegalArgumentException("Multipart parts carry their own body");
  if(who!=null)b.header("Authorization","Bearer "+tokens.login(who).accessToken());
  return mvc.perform(b).andReturn();
 }
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
 @Test void csvExportUsesAuthorizedFullCollectionQueryAndEscapesCells()throws Exception{
  var created=new ArrayList<AssetService.View>();
  for(int i=0;i<103;i++){clock.now=clock.now.plusSeconds(1);created.add(create("Äpfel-"+i,true));}
  var foreignLocation=places.create(PlaceService.Kind.LOCATION,other,"Foreign",actor);
  assets.register(other,new AssetService.Registration(new AssetService.ProductSelection(product),foreignLocation,null,true,"Äpfel foreign",null,null),actor);
  var formula=assets.register(tenant,new AssetService.Registration(new AssetService.ProductSelection(product),location,null,true,"=HYPERLINK(\"x\",\"y\")",null,null),actor);
  var user=principal(Scope.TENANT,Role.USER,Status.ACTIVE);
  var response=request(user,get("/api/v1/asset-csv/export").param("q","Äpfel").param("page","4").param("size","1").param("sort","tag").param("direction","asc").param("delimiter",";"),null);
  assertEquals(200,status(response));assertTrue(response.getResponse().getContentType().startsWith("text/csv"));
  String csv=new String(response.getResponse().getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8);
  assertTrue(csv.startsWith("\ufeffid;assetTag;"));assertEquals(104,csv.split("\r\n").length);
  assertTrue(csv.contains("Äpfel-0"));assertFalse(csv.contains("Äpfel foreign"));assertFalse(csv.contains(formula.id().toString()));
  assertTrue(csv.indexOf(created.getFirst().id().toString())<csv.indexOf(created.getLast().id().toString()));
  var forged=request(user,get("/api/v1/asset-csv/export").param("tenantId",other.toString()).param("q","Äpfel foreign"),null);
  assertEquals(200,status(forged));assertFalse(new String(forged.getResponse().getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8).contains("Äpfel foreign"));
  assertEquals(401,status(request(null,get("/api/v1/asset-csv/export"),null)));
  var global=request(admin,get("/api/v1/asset-csv/export").param("q","Äpfel foreign"),null);
  assertEquals(200,status(global));assertTrue(new String(global.getResponse().getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8).contains("Äpfel foreign"));
  assertEquals(200,status(request(principal(Scope.GLOBAL,Role.USER,Status.ACTIVE),get("/api/v1/asset-csv/export").param("tenantId",tenant.toString()).param("q","Äpfel-0"),null)));
  assertEquals(403,status(request(principal(Scope.TENANT,Role.USER,Status.PENDING),get("/api/v1/asset-csv/export"),null)));
  var safe=request(admin,get("/api/v1/asset-csv/export").param("tenantId",tenant.toString()).param("q","HYPERLINK"),null);
  assertEquals(200,status(safe));assertTrue(new String(safe.getResponse().getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8).contains("'=HYPERLINK(\"\"x\"\",\"\"y\"\")"));
  assertEquals(400,status(request(admin,get("/api/v1/asset-csv/export").param("delimiter","\n"),null)));
  assertEquals(400,status(request(admin,get("/api/v1/asset-csv/export").param("sort","tenantId"),null)));
 }
 @Test void legacyCsvPreviewRequiresGlobalAdminAndResolvesOnlyAuthorizedReferences()throws Exception{
  byte[] csv="Kennung;Modell;Raum;Status;Zustand;Ausleihbar;Datum\nAlt-1;Product;Room;alt;kaputt;ja;01.02.2017\nAlt-2;Missing;Room;alt;kaputt;ja;01.02.2017\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var file=new org.springframework.mock.web.MockMultipartFile("file","legacy.csv","text/csv",csv);
  var parsed=request(admin,multipart("/api/v1/asset-csv/parse-preview").file(file),null);
  assertEquals(200,status(parsed));String hash=(String)data(parsed).get("sha256");
  assertEquals(2,((Number)data(parsed).get("totalRows")).intValue());
  assertEquals(403,status(request(principal(Scope.GLOBAL,Role.USER,Status.ACTIVE),multipart("/api/v1/asset-csv/parse-preview").file(file),null)));
  assertEquals(403,status(request(principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE),multipart("/api/v1/asset-csv/parse-preview").file(file),null)));
  assertEquals(401,status(request(null,multipart("/api/v1/asset-csv/parse-preview").file(file),null)));
  Map<String,Object> config=new LinkedHashMap<>();
  config.put("tenantId",tenant);config.put("sha256",hash);config.put("encoding","UTF-8");config.put("delimiter",";");
  config.put("columns",Map.of("localIdentifier","Kennung","productName","Modell","locationName","Raum",
      "lifecycle","Status","condition","Zustand","loanable","Ausleihbar","tagDate","Datum"));
  config.put("values",Map.of("lifecycle",Map.of("alt","DEREGISTERED"),"condition",Map.of("kaputt","DEFECTIVE"),"loanable",Map.of("ja","true")));
  config.put("choices",Map.of("productName",Map.of("Product",product)));
  var response=csvPreview(admin,file,config);
  assertEquals(200,status(response),response.getResponse().getContentAsString());
  var body=data(response);assertEquals(2,((Number)body.get("totalRows")).intValue());assertEquals(1,((Number)body.get("warnings")).intValue(),response.getResponse().getContentAsString());assertEquals(1,((Number)body.get("errors")).intValue());
  var rows=(List<Map<String,Object>>)body.get("rows");assertEquals("WARNING",rows.getFirst().get("status"));assertEquals("ERROR",rows.getLast().get("status"));
  var refs=(Map<String,Object>)rows.getFirst().get("references");assertEquals(product.toString(),refs.get("productId"));assertEquals(category.toString(),refs.get("categoryId"));assertEquals(manufacturer.toString(),refs.get("manufacturerId"));assertEquals(location.toString(),refs.get("locationId"));
  assertEquals("2017-02-01",((Map<?,?>)rows.getFirst().get("normalized")).get("tagDate"));
  assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM asset WHERE local_identifier LIKE 'Alt-%'",Long.class));
  config.put("sha256","0".repeat(64));assertEquals(400,status(csvPreview(admin,file,config)));
  config.put("sha256",hash);
  config.put("columns",Map.of("productName","Modell","manufacturerName","Kennung","locationName","Raum"));
  assertEquals(400,status(csvPreview(admin,file,config)));
  config.put("columns",Map.of("productId","Kennung","productName","Modell","locationName","Raum"));
  assertEquals(400,status(csvPreview(admin,file,config)));
  config.put("columns",Map.of("productName","Modell","locationName","Modell"));
  assertEquals(400,status(csvPreview(admin,file,config)));
 }
 @SuppressWarnings("unchecked") MvcResult csvPreview(Identity who,org.springframework.mock.web.MockMultipartFile file,Map<String,Object> config)throws Exception{
  var settings=new org.springframework.mock.web.MockMultipartFile("config","","application/json",json.writeValueAsBytes(config));
  return request(who,multipart("/api/v1/asset-csv/preview").file(file).file(settings),null);
 }
 MvcResult csvExecute(Identity who,org.springframework.mock.web.MockMultipartFile file,Map<String,Object> preview,List<Long> selected)throws Exception{
  var config=new org.springframework.mock.web.MockMultipartFile("config","","application/json",
      json.writeValueAsBytes(Map.of("preview",preview,"selectedRows",selected)));
  return request(who,multipart("/api/v1/asset-csv/execute").file(file).file(config),null);
 }
 @Test void legacyCsvExecuteCommitsSelectedRowsAndTruthfulInitialHistory()throws Exception{
  String prefix="legacy-"+UUID.randomUUID();
  byte[] bytes=("Kennung;Produkt;Raum;Status;Zustand;Ausleihbar;Datum\n"
      +prefix+"-1;"+product+";"+location+";DEREGISTERED;DEFECTIVE;true;01.02.2017\n"
      +prefix+"-bad;bad-uuid;"+location+";REGISTERED;UNKNOWN;false;01.02.2017\n"
      +prefix+"-2;"+product+";"+location+";DEPLOYED;WORKING;false;01.02.2017\n")
      .getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var file=new org.springframework.mock.web.MockMultipartFile("file","legacy.csv","text/csv",bytes);
  String hash=(String)data(request(admin,multipart("/api/v1/asset-csv/parse-preview").file(file),null)).get("sha256");
  var config=new LinkedHashMap<String,Object>();
  config.put("tenantId",tenant);config.put("sha256",hash);config.put("encoding","UTF-8");config.put("delimiter",";");
  config.put("columns",Map.of("localIdentifier","Kennung","productId","Produkt","locationId","Raum",
      "lifecycle","Status","condition","Zustand","loanable","Ausleihbar","tagDate","Datum"));
  assertEquals(403,status(csvExecute(principal(Scope.TENANT,Role.ADMIN,Status.ACTIVE),file,config,List.of(2L,3L,4L))));
  assertEquals(403,status(csvExecute(principal(Scope.GLOBAL,Role.USER,Status.ACTIVE),file,config,List.of(2L,3L,4L))));
  assertEquals(401,status(csvExecute(null,file,config,List.of(2L,3L,4L))));
  var response=csvExecute(admin,file,config,List.of(2L,3L,4L));
  assertEquals(200,status(response),response.getResponse().getContentAsString());
  var result=data(response);assertEquals(2,((Number)result.get("successful")).intValue());
  assertEquals(1,((Number)result.get("failed")).intValue());assertEquals(0,((Number)result.get("skipped")).intValue());
  assertTrue(((String)result.get("failureCsv")).startsWith("\ufeff"));
  assertTrue(((String)result.get("failureCsv")).contains(prefix+"-bad"));
  var imported=jdbc.query("SELECT id,asset_tag,local_identifier,lifecycle,condition,loanable,loaned_to,loaned_at,expected_return_at FROM asset WHERE local_identifier LIKE ? ORDER BY local_identifier",
      (rs,n)->Map.<String,Object>of("id",rs.getObject("id",UUID.class),"tag",rs.getString("asset_tag"),
          "local",rs.getString("local_identifier"),"lifecycle",rs.getString("lifecycle"),
          "condition",rs.getString("condition"),"loanable",rs.getBoolean("loanable")),prefix+"%");
  assertEquals(2,imported.size());assertTrue(((String)imported.getFirst().get("tag")).startsWith("20170201-"));
  assertEquals("DEREGISTERED",imported.getFirst().get("lifecycle"));assertEquals(true,imported.getFirst().get("loanable"));
  assertEquals("DEPLOYED",imported.getLast().get("lifecycle"));
  for(var row:imported){
   UUID id=(UUID)row.get("id");assertEquals(1L,events(id));assertEquals(tenant,attribution("ASSET_LEGACY_IMPORTED",id));
   assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM asset WHERE id=? AND (loaned_to IS NOT NULL OR loaned_at IS NOT NULL OR expected_return_at IS NOT NULL OR loan_note IS NOT NULL)",Long.class,id));
   String event=jdbc.queryForObject("SELECT data::text FROM asset_event WHERE asset_id=?",String.class,id);
   assertTrue(event.contains("LEGACY_CSV"));assertTrue(event.contains("\"before\": null"));
   assertTrue(event.contains("\"loanedTo\": null"));
  }
  config.put("sha256","0".repeat(64));assertEquals(400,status(csvExecute(admin,file,config,List.of(2L))));
  config.put("sha256",hash);assertEquals(400,status(csvExecute(admin,file,config,List.of(2L,2L))));
  var repeated=data(csvExecute(admin,file,config,List.of(2L)));
  assertEquals(1,((Number)repeated.get("successful")).intValue());
  assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM asset WHERE local_identifier=?",Long.class,prefix+"-1"));
 }
 @Test void legacyCsvExecuteRechecksStaleReferenceAndContinuesLaterRow()throws Exception{
  UUID stale=places.create(PlaceService.Kind.LOCATION,tenant,"Will delete",actor);
  byte[] bytes=("Kennung;Produkt;Raum\n=1+1;"+product+";"+stale+"\n"
      +UUID.randomUUID()+";"+product+";"+location+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var file=new org.springframework.mock.web.MockMultipartFile("file","legacy.csv","text/csv",bytes);
  String hash=(String)data(request(admin,multipart("/api/v1/asset-csv/parse-preview").file(file),null)).get("sha256");
  var config=new LinkedHashMap<String,Object>();config.put("tenantId",tenant);config.put("sha256",hash);
  config.put("encoding","UTF-8");config.put("delimiter",";");
  config.put("columns",Map.of("localIdentifier","Kennung","productId","Produkt","locationId","Raum"));
  assertEquals(0,((Number)data(csvPreview(admin,file,config)).get("errors")).intValue());
  places.delete(PlaceService.Kind.LOCATION,tenant,stale,actor);
  var result=data(csvExecute(admin,file,config,List.of(2L,3L)));
  assertEquals(1,((Number)result.get("successful")).intValue());
  assertEquals(1,((Number)result.get("failed")).intValue());
  assertTrue(((String)result.get("failureCsv")).contains("'=1+1"));
  assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM asset WHERE location_id=?",Long.class,stale));
 }
 @Test void legacyCsvHistoricalTagAllocationIsUniqueUnderConcurrency()throws Exception{
  var values=new HashMap<String,Object>();values.put("tagDate","2017-02-01");values.put("lifecycle","REGISTERED");
  values.put("condition","UNKNOWN");values.put("loanable",false);
  var refs=Map.of("productId",product,"categoryId",category,"manufacturerId",manufacturer,"locationId",location);
  var pool=Executors.newFixedThreadPool(4);
  try{
   var jobs=new ArrayList<Future<AssetCsvImportRowWriter.Created>>();
   for(int i=0;i<8;i++){
    long row=i+2;jobs.add(pool.submit(()->csvWriter.write(new AssetCsvImportRowWriter.Input(tenant,row,values,refs),actor)));
   }
   var tags=new HashSet<String>();for(var job:jobs)tags.add(job.get(30,TimeUnit.SECONDS).tag());
   assertEquals(8,tags.size());assertTrue(tags.stream().allMatch(tag->tag.startsWith("20170201-")));
  }finally{pool.shutdownNow();}
 }
 @Test void legacyCsvRowFailureRollsBackHistoricalTagAllocation(){
  var values=new HashMap<String,Object>();values.put("tagDate","2016-01-01");values.put("lifecycle","REGISTERED");
  values.put("condition","UNKNOWN");values.put("loanable",false);
  var refs=Map.of("productId",product,"categoryId",category,"manufacturerId",manufacturer,"locationId",location);
  // A failed trusted-context call occurs after allocation; the row transaction must roll that allocation back.
  assertThrows(NullPointerException.class,()->csvWriter.write(new AssetCsvImportRowWriter.Input(tenant,2,values,refs),null));
  assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM asset_tag_counter WHERE registration_date='2016-01-01'",Long.class));
  assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM asset WHERE asset_tag LIKE '20160101-%'",Long.class));
 }
 @Test void legacyCsvDatabaseRowFailureDoesNotStopLaterRows()throws Exception{
  byte[] bytes=("Kennung;Produkt;Raum;Datum\n=1+1;"+product+";"+location+";2015-04-03\n"
      +"after-failure-"+UUID.randomUUID()+";"+product+";"+location+";2015-04-03\n")
      .getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var file=new org.springframework.mock.web.MockMultipartFile("file","legacy.csv","text/csv",bytes);
  String hash=(String)data(request(admin,multipart("/api/v1/asset-csv/parse-preview").file(file),null)).get("sha256");
  var config=Map.<String,Object>of("tenantId",tenant,"sha256",hash,"encoding","UTF-8","delimiter",";",
      "columns",Map.of("localIdentifier","Kennung","productId","Produkt","locationId","Raum","tagDate","Datum"));
  jdbc.execute("CREATE FUNCTION gate3_reject_asset() RETURNS trigger AS $$ BEGIN IF NEW.local_identifier = '=1+1' THEN RAISE EXCEPTION 'fixture row rejected' USING ERRCODE = '23514'; END IF; RETURN NEW; END; $$ LANGUAGE plpgsql");
  jdbc.execute("CREATE TRIGGER gate3_reject_asset BEFORE INSERT ON asset FOR EACH ROW EXECUTE FUNCTION gate3_reject_asset()");
  try{
   var response=csvExecute(admin,file,config,List.of(2L,3L));
   assertEquals(200,status(response),response.getResponse().getContentAsString());
   var result=data(response);assertEquals(1,((Number)result.get("successful")).intValue());
   assertEquals(1,((Number)result.get("failed")).intValue());
   assertTrue(((String)result.get("failureCsv")).contains("'=1+1"));
   assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM asset WHERE local_identifier='=1+1'",Long.class));
   assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM asset WHERE asset_tag LIKE '20150403-%'",Long.class));
  }finally{
   jdbc.execute("DROP TRIGGER gate3_reject_asset ON asset");
   jdbc.execute("DROP FUNCTION gate3_reject_asset()");
  }
 }
 @Test void legacyCsvPreviewAmbiguityExplicitChoiceAndUuidScope()throws Exception{
  UUID duplicate=catalog.createProduct(tenant,category,manufacturer,"Product",null,actor);
  byte[] csv="Modell;Raum\nProduct;Room\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var file=new org.springframework.mock.web.MockMultipartFile("file","legacy.csv","text/csv",csv);
  String hash=(String)data(request(admin,multipart("/api/v1/asset-csv/parse-preview").file(file),null)).get("sha256");
  var config=new LinkedHashMap<String,Object>();
  config.put("tenantId",tenant);config.put("sha256",hash);config.put("encoding","UTF-8");config.put("delimiter",";");
  config.put("columns",Map.of("productName","Modell","locationName","Raum"));
  var unresolved=data(csvPreview(admin,file,config));
  assertEquals("ERROR",((List<Map<String,Object>>)unresolved.get("rows")).getFirst().get("status"));
  config.put("choices",Map.of("productName",Map.of("Product",duplicate)));
  var chosen=data(csvPreview(admin,file,config));
  var row=((List<Map<String,Object>>)chosen.get("rows")).getFirst();
  assertEquals("WARNING",row.get("status"));assertEquals(duplicate.toString(),((Map<?,?>)row.get("references")).get("productId"));
  config.put("choices",Map.of("productName",Map.of("Product",UUID.randomUUID())));
  assertEquals("ERROR",((List<Map<String,Object>>)data(csvPreview(admin,file,config)).get("rows")).getFirst().get("status"));
  config.remove("choices");
  UUID foreignLocation=places.create(PlaceService.Kind.LOCATION,other,"Foreign",actor);
  byte[] uuids=("Produkt;Raum\n"+product+";"+foreignLocation+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var uuidFile=new org.springframework.mock.web.MockMultipartFile("file","uuid.csv","text/csv",uuids);
  config.put("sha256",data(request(admin,multipart("/api/v1/asset-csv/parse-preview").file(uuidFile),null)).get("sha256"));
  config.put("columns",Map.of("productId","Produkt","locationId","Raum"));
  var foreign=data(csvPreview(admin,uuidFile,config));
  assertEquals("ERROR",((List<Map<String,Object>>)foreign.get("rows")).getFirst().get("status"));
  byte[] direct=("Kategorie;Hersteller;Raum\n"+category+";"+manufacturer+";"+location+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var directFile=new org.springframework.mock.web.MockMultipartFile("file","direct.csv","text/csv",direct);
  config.put("sha256",data(request(admin,multipart("/api/v1/asset-csv/parse-preview").file(directFile),null)).get("sha256"));
  config.put("columns",Map.of("categoryId","Kategorie","manufacturerId","Hersteller","locationId","Raum"));
  assertEquals("WARNING",((List<Map<String,Object>>)data(csvPreview(admin,directFile,config)).get("rows")).getFirst().get("status"));
  UUID transientLocation=places.create(PlaceService.Kind.LOCATION,tenant,"Transient",actor);
  byte[] stale=("Produkt;Raum\n"+product+";Transient\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var staleFile=new org.springframework.mock.web.MockMultipartFile("file","stale.csv","text/csv",stale);
  config.put("sha256",data(request(admin,multipart("/api/v1/asset-csv/parse-preview").file(staleFile),null)).get("sha256"));
  config.put("columns",Map.of("productId","Produkt","locationName","Raum"));
  assertEquals("WARNING",((List<Map<String,Object>>)data(csvPreview(admin,staleFile,config)).get("rows")).getFirst().get("status"));
  places.delete(PlaceService.Kind.LOCATION,tenant,transientLocation,actor);
  assertEquals("ERROR",((List<Map<String,Object>>)data(csvPreview(admin,staleFile,config)).get("rows")).getFirst().get("status"));
 }
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
