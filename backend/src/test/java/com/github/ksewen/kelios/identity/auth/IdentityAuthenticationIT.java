package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.audit.*;
import com.github.ksewen.kelios.catalog.CatalogService;
import com.github.ksewen.kelios.identity.UserService;
import com.github.ksewen.kelios.identity.UserService.*;
import com.github.ksewen.kelios.inventory.*;
import com.github.ksewen.kelios.platform.security.*;
import com.github.ksewen.kelios.tenancy.TenantService;
import jakarta.servlet.http.Cookie;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
class IdentityAuthenticationIT {
    @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17-alpine");
    static final class MutableClock extends Clock {
        final AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-10-10T12:00:00Z"));
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}public Instant instant(){return now.get();}
    }
    static final MutableClock CLOCK=new MutableClock();
    static final AuthTestKeys KEYS;static final HttpsOidcFixture PROVIDER;
    static {try{KEYS=new AuthTestKeys("identity-it-keys");PROVIDER=new HttpsOidcFixture(KEYS.directory,CLOCK);}catch(Exception ex){throw new ExceptionInInitializerError(ex);}}
    @TestConfiguration static class Time {@Bean @Primary MutableClock identityClock(){return CLOCK;}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",PG::getJdbcUrl);p.add("spring.datasource.username",PG::getUsername);p.add("spring.datasource.password",PG::getPassword);
        p.add("kelios.auth.key-directory",()->KEYS.directory.toAbsolutePath().toString());p.add("kelios.auth.public-origin",()->"https://kelios.example");p.add("kelios.auth.issuer",()->"https://kelios.example");
        p.add("kelios.auth.trusted-provider-hosts",()->"localhost");p.add("kelios.auth.provider-trust-store",()->PROVIDER.trustStore.toAbsolutePath().toString());p.add("kelios.auth.provider-trust-store-password",()->"fixture-store");
    }
    @Autowired AuthenticationService auth;@Autowired OidcConfigurationService configurations;@Autowired OidcLogin oidc;@Autowired OidcProvisioning provisioning;
    @Autowired JwtTokens tokens;@Autowired IdentityStore identities;@Autowired UserService users;@Autowired TenantService tenants;
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager tm;@Autowired WebApplicationContext context;
    @Autowired SecuredDomainOperations secured;@Autowired CatalogService catalog;@Autowired PlaceService places;@Autowired AssetService assets;
    MockMvc mvc;Identity admin;UUID tenant,other;final JsonMapper json=JsonMapper.builder().build();
    @BeforeEach void setup(){
        CLOCK.now.updateAndGet(i->i.plusSeconds(120));PROVIDER.reset();SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM external_identity");jdbc.update("DELETE FROM local_global_credential");jdbc.update("DELETE FROM app_user");
        jdbc.update("DELETE FROM tenant_oidc_allowed_group");jdbc.update("DELETE FROM tenant_oidc_config");
        admin=auth.bootstrap("initial-admin@example.org","Administrator","CorrectHorse25!Battery");
        tenant=tenants.create("Tenant",admin.actor());other=tenants.create("Other",admin.actor());
        configure(tenant,null,Set.of(PROVIDER.group));mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @AfterAll static void close(){PROVIDER.close();}
    OidcConfigurationService.View configure(UUID target,Long version,Set<UUID> groups){return configurations.put(admin,target,PROVIDER.issuer,"fixture-client","fixture-secret",true,version,groups);}
    <T>T tx(java.util.function.Supplier<T> work){return new TransactionTemplate(tm).execute(s->work.get());}
    void principal(Identity user){SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user,null,List.of()));}
    Identity fixture(Scope scope,UUID tenant,Role role,Status status){UUID id=users.create(scope,tenant,role,status,"Fixture user");return identities.load(id,"");}
    Map<String,Object> body(MvcResult result){return json.readValue(result.getResponse().getContentAsByteArray(),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});}
    Cookie cookie(MvcResult result,String name){for(String header:result.getResponse().getHeaders("Set-Cookie")){String[] parts=header.split(";",2)[0].split("=",2);if(parts[0].equals(name)){Cookie cookie=new Cookie(name,parts[1]);cookie.setSecure(true);cookie.setHttpOnly(true);return cookie;}}throw new AssertionError("Expected cookie absent");}
    MvcResult unsafe(MockHttpServletRequestBuilder command,String bearer,Cookie... cookies)throws Exception{
        var csrfRequest=get("/api/v1/auth/csrf").secure(true);
        if(cookies.length>0)csrfRequest.cookie(cookies);
        var csrf=mvc.perform(csrfRequest).andReturn();
        var proof=cookie(csrf,BrowserTokens.CSRF);List<Cookie> all=new ArrayList<>(List.of(cookies));all.add(proof);
        command.secure(true).header("Origin","https://kelios.example").header("X-CSRF-Token",body(csrf).get("token")).cookie(all.toArray(Cookie[]::new));
        if(bearer!=null)command.header("Authorization","Bearer "+bearer);return mvc.perform(command).andReturn();
    }
    record LoginFlow(MvcResult start,HttpsOidcFixture.Authorization authorization){}
    LoginFlow flow()throws Exception{
        var start=unsafe(post("/api/v1/auth/oidc/"+tenant+"/start"),null);
        assertEquals(200,start.getResponse().getStatus());return new LoginFlow(start,PROVIDER.authorize((String)body(start).get("authorizationUrl")));
    }
    MvcResult callback(LoginFlow flow,String state)throws Exception{
        return mvc.perform(get("/api/v1/auth/oidc/"+tenant+"/callback").secure(true).cookie(cookie(flow.start(),BrowserTokens.FLOW))
            .param("state",state).param("code",flow.authorization().code()).param("iss",PROVIDER.issuer)).andReturn();
    }
    MvcResult loginOidc()throws Exception{var flow=flow();return callback(flow,flow.authorization().state());}
    @Test void globalLoginCurrentUserAndCookieContract()throws Exception{
        var result=unsafe(post("/api/v1/auth/local/login").contentType("application/json").content(json.writeValueAsString(Map.of("email","INITIAL-ADMIN@example.org","password","CorrectHorse25!Battery"))),null);
        assertEquals(200,result.getResponse().getStatus());assertFalse(body(result).containsKey("refreshToken"));String access=(String)body(result).get("accessToken");
        var me=mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+access)).andReturn();assertEquals(200,me.getResponse().getStatus());assertEquals("GLOBAL",body(me).get("scope"));
        String setCookie=result.getResponse().getHeader("Set-Cookie");assertTrue(setCookie.contains("Secure") && setCookie.contains("HttpOnly") && setCookie.contains("SameSite=Strict") && !setCookie.contains("Domain="));
        var refresh=unsafe(post("/api/v1/auth/session/refresh"),null,cookie(result,BrowserTokens.REFRESH));assertEquals(200,refresh.getResponse().getStatus());
        assertEquals(tokens.verify(cookie(result,BrowserTokens.REFRESH).getValue(),"refresh").expiresAt(),tokens.verify(cookie(refresh,BrowserTokens.REFRESH).getValue(),"refresh").expiresAt());
        var logout=unsafe(delete("/api/v1/auth/session"),access,cookie(refresh,BrowserTokens.REFRESH));assertEquals(200,logout.getResponse().getStatus());assertTrue(logout.getResponse().getHeaders("Set-Cookie").stream().allMatch(x->x.contains("Max-Age=0")));
    }
    @Test void csrfCorsMissingAndForgedTokensAndUnknownRoutes()throws Exception{
        assertEquals(403,mvc.perform(post("/api/v1/auth/local/login").contentType("application/json").content("{}")).andReturn().getResponse().getStatus());
        var csrf=mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        assertEquals(403,mvc.perform(post("/api/v1/auth/session/refresh").header("Origin","https://evil.example").header("X-CSRF-Token",body(csrf).get("token")).cookie(cookie(csrf,BrowserTokens.CSRF))).andReturn().getResponse().getStatus());
        assertEquals(401,mvc.perform(get("/api/v1/auth/me")).andReturn().getResponse().getStatus());assertEquals(401,mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer forged")).andReturn().getResponse().getStatus());
        assertEquals(404,mvc.perform(get("/api/v1/not-implemented")).andReturn().getResponse().getStatus());assertEquals(200,mvc.perform(get("/api/v1/status")).andReturn().getResponse().getStatus());
        assertNull(mvc.perform(get("/api/v1/status").header("Origin","https://evil.example")).andReturn().getResponse().getHeader("Access-Control-Allow-Origin"));assertNull(csrf.getRequest().getSession(false));
    }
    @Test void noDefaultPasswordAndBadLoginIsGeneric(){
        var absent=assertThrows(AuthFailure.class,()->auth.login("unknown-user@example.org","WrongPassword25!"));var bad=assertThrows(AuthFailure.class,()->auth.login("initial-admin@example.org","WrongPassword25!"));assertEquals(absent.getMessage(),bad.getMessage());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM local_global_credential",Integer.class));
    }
    @Test void globalCreationIsActiveUserAndSeparateGrantAndNoTenantCredentials(){
        var created=auth.createGlobal(admin,"Another.Admin@example.org","Another","CorrectHorse25!Battery");assertEquals(Scope.GLOBAL,created.scope());assertNull(created.tenantId());assertEquals(Role.USER,created.role());assertEquals(Status.ACTIVE,created.status());
        assertEquals(Role.ADMIN,auth.role(admin,created.id(),Role.ADMIN).role());var tenantUser=fixture(Scope.TENANT,tenant,Role.USER,Status.ACTIVE);
        assertThrows(AuthFailure.class,()->auth.resetCredential(admin,tenantUser.id(),"tenant-local@example.org","CorrectHorse25!Battery"));
        assertThrows(RuntimeException.class,()->jdbc.update("INSERT INTO local_global_credential(user_id,email,password_hash,email_verified_at) VALUES(?,'tenant-local@example.org','invalid',now())",tenantUser.id()));
    }
    @Test void passwordPolicyEveryCredentialPathAndChangeDoesNotRevoke(){
        var issued=auth.login("initial-admin@example.org","CorrectHorse25!Battery");String invalid="nocapitalletters25!";
        assertThrows(AuthFailure.class,()->auth.bootstrap("recover@example.org","Recovery",invalid));assertThrows(AuthFailure.class,()->auth.createGlobal(admin,"new-global@example.org","New",invalid));
        assertThrows(AuthFailure.class,()->auth.resetCredential(admin,admin.id(),"initial-admin@example.org",invalid));assertThrows(AuthFailure.class,()->auth.ownPassword(admin,"CorrectHorse25!Battery",invalid));
        auth.ownPassword(admin,"CorrectHorse25!Battery","DifferentHorse25!Battery");assertThrows(AuthFailure.class,()->auth.login("initial-admin@example.org","CorrectHorse25!Battery"));
        assertDoesNotThrow(()->auth.refresh(issued.refreshToken()));assertDoesNotThrow(()->tokens.verify(issued.accessToken(),"access"));
        assertDoesNotThrow(()->auth.login("initial-admin@example.org","DifferentHorse25!Battery"));assertTrue(auditCount("LOCAL_CREDENTIAL_CHANGED",admin.id())>0);
    }
    long auditCount(String action,UUID id){return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action=? AND target_id=?",Long.class,action,id);}
    @Test void bootstrapRefusesUsableAdminButLaterRecoversAndAudits(){
        assertThrows(AuthFailure.class,()->auth.bootstrap("second-admin@example.org","Second","CorrectHorse25!Battery"));
        jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=?",admin.id());
        var recovered=auth.bootstrap("recovered-admin@example.org","Recovery","CorrectHorse25!Battery");assertEquals(Role.ADMIN,recovered.role());assertEquals(Status.ACTIVE,recovered.status());assertEquals(1,auditCount("GLOBAL_ADMIN_BOOTSTRAPPED",recovered.id()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM global_admin_recovery_guard",Integer.class));assertEquals(List.of("id"),jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='global_admin_recovery_guard'",String.class));
    }
    @Test void malformedCredentialAdminDoesNotBlockRecoveryAndFailureRollsBack(){
        jdbc.update("UPDATE local_global_credential SET password_hash='malformed' WHERE user_id=?",admin.id());long count=jdbc.queryForObject("SELECT count(*) FROM app_user",Long.class);
        assertThrows(RuntimeException.class,()->auth.bootstrap("initial-admin@example.org","Collision","CorrectHorse25!Battery"));assertEquals(count,jdbc.queryForObject("SELECT count(*) FROM app_user",Long.class));
        assertDoesNotThrow(()->auth.bootstrap("repair-admin@example.org","Recovery","CorrectHorse25!Battery"));
    }
    @Test void selfDisableDemotionRejectedWithoutLastAdminInvariant(){
        assertThrows(AuthFailure.class,()->auth.disable(admin,admin.id()));assertThrows(AuthFailure.class,()->auth.role(admin,admin.id(),Role.USER));
        var second=auth.createGlobal(admin,"other-admin@example.org","Other","CorrectHorse25!Battery");var promoted=auth.role(admin,second.id(),Role.ADMIN);
        auth.disable(admin,promoted.id());auth.disable(promoted,admin.id());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM app_user WHERE scope='GLOBAL' AND role='ADMIN' AND status='ACTIVE'",Integer.class));
        assertDoesNotThrow(()->auth.bootstrap("recovery-third@example.org","Recovery","CorrectHorse25!Battery"));
    }
    @Test void concurrentRecoveryCommandsCreateExactlyOne()throws Exception{
        jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=?",admin.id());
        try(var pool=Executors.newFixedThreadPool(2)){
            var gate=new CountDownLatch(1);var first=pool.submit(()->{gate.await();try{auth.bootstrap("recover-one@example.org","One","CorrectHorse25!Battery");return true;}catch(AuthFailure ex){return false;}});
            var second=pool.submit(()->{gate.await();try{auth.bootstrap("recover-two@example.org","Two","CorrectHorse25!Battery");return true;}catch(AuthFailure ex){return false;}});gate.countDown();
            assertNotEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM app_user WHERE scope='GLOBAL' AND role='ADMIN' AND status='ACTIVE'",Integer.class));
    }
    @Test void oidcConfigurationEncryptedSanitizedAndVersioned()throws Exception{
        var view=configurations.get(admin,tenant);assertEquals(Set.of(PROVIDER.group),view.allowedGroupIds());assertEquals("https://kelios.example/api/v1/auth/oidc/"+tenant+"/callback",view.callbackUri());
        assertFalse(jdbc.queryForObject("SELECT encrypted_client_secret FROM tenant_oidc_config WHERE tenant_id=?",String.class,tenant).contains("fixture-secret"));assertFalse(json.writeValueAsString(view).contains("fixture-secret"));
        assertThrows(AuthFailure.class,()->configure(tenant,null,Set.of(PROVIDER.group)));
        var updated=configurations.put(admin,tenant,PROVIDER.issuer,"fixture-client",null,true,view.version(),Set.of(PROVIDER.group));assertEquals(1,updated.version());
        assertEquals(tenant,configurations.get(fixture(Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE),tenant).tenantId());
        var result=mvc.perform(get("/api/v1/tenants/"+tenant+"/oidc").header("Authorization","Bearer "+tokens.login(admin).accessToken())).andReturn();assertEquals(200,result.getResponse().getStatus());assertFalse(body(result).containsKey("encryptedSecret"));
        String input=json.writeValueAsString(Map.of("issuer",PROVIDER.issuer,"clientId","fixture-client","enabled",true,"version",1,"allowedGroupIds",List.of(PROVIDER.group)));
        String forged=input.substring(0,input.length()-1)+",\"actorId\":\""+UUID.randomUUID()+"\"}";
        assertEquals(400,mvc.perform(put("/api/v1/tenants/"+tenant+"/oidc").header("Authorization","Bearer "+tokens.login(admin).accessToken()).contentType("application/json").content(forged)).andReturn().getResponse().getStatus());
        assertEquals(1,configurations.get(admin,tenant).version());
        var saved=mvc.perform(put("/api/v1/tenants/"+tenant+"/oidc").header("Authorization","Bearer "+tokens.login(admin).accessToken()).contentType("application/json").content(input)).andReturn();
        assertEquals(200,saved.getResponse().getStatus());assertEquals(2,((Number)body(saved).get("version")).intValue());assertEquals(true,body(saved).get("secretConfigured"));assertFalse(body(saved).containsKey("clientSecret"));
        var tenantAdmin=fixture(Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE);
        assertEquals(409,mvc.perform(put("/api/v1/tenants/"+tenant+"/oidc").header("Authorization","Bearer "+tokens.login(tenantAdmin).accessToken()).contentType("application/json").content(input)).andReturn().getResponse().getStatus());
        String malformed=input.replace(PROVIDER.group.toString(),"invalid-group-uuid");
        assertEquals(400,mvc.perform(put("/api/v1/tenants/"+tenant+"/oidc").header("Authorization","Bearer "+tokens.login(admin).accessToken()).contentType("application/json").content(malformed)).andReturn().getResponse().getStatus());
        assertEquals(2,configurations.get(admin,tenant).version());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='OIDC_CONFIG_CHANGED' AND target_id=? AND actor_id<>?",Integer.class,tenant,admin.id()));
    }
    @Test void fullDocumentedHttpsOidcFirstLoginIsPendingAndBusinessDenied()throws Exception{
        var result=loginOidc();assertEquals("https://kelios.example/auth/complete",result.getResponse().getHeader("Location"));
        var refresh=cookie(result,BrowserTokens.REFRESH).getValue();var session=auth.refresh(refresh);assertEquals(Scope.TENANT,session.user().scope());assertEquals(tenant,session.user().tenantId());assertEquals(Role.USER,session.user().role());assertEquals(Status.PENDING,session.user().status());
        assertEquals(403,mvc.perform(get("/api/v1/tenants").header("Authorization","Bearer "+session.accessToken())).andReturn().getResponse().getStatus());assertEquals(200,mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+session.accessToken())).andReturn().getResponse().getStatus());
        assertNull(result.getRequest().getSession(false));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM external_identity",Integer.class));
    }
    @Test void existingIdentityKeepsIdDespiteChangedNameAndApprovedThenDisabled()throws Exception{
        var first=auth.refresh(cookie(loginOidc(),BrowserTokens.REFRESH).getValue());UUID id=first.user().id();auth.approve(admin,id);PROVIDER.name="Changed display";
        var active=auth.refresh(cookie(loginOidc(),BrowserTokens.REFRESH).getValue());assertEquals(id,active.user().id());assertEquals(Status.ACTIVE,active.user().status());assertEquals("Fixture user",active.user().displayName());
        auth.disable(admin,id);assertThrows(AuthFailure.class,()->auth.refresh(active.refreshToken()));assertDoesNotThrow(()->tokens.verify(active.accessToken(),"access"));
        assertEquals("https://kelios.example/auth/failed",loginOidc().getResponse().getHeader("Location"));
    }
    @ParameterizedTest(name="provider rejection {0}") @ValueSource(strings={"nonce","id-issuer","id-audience","id-expired","subject-mismatch","token-unavailable","userinfo-unavailable"})
    void oidcProtocolFailuresProvisionNothing(String mode)throws Exception{
        PROVIDER.mode=mode;var result=loginOidc();assertEquals("https://kelios.example/auth/failed",result.getResponse().getHeader("Location"));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM external_identity",Integer.class));assertTrue(result.getResponse().getHeaders("Set-Cookie").stream().noneMatch(x->x.startsWith(BrowserTokens.REFRESH+"=")));
    }
    @ParameterizedTest(name="group rejection {0}") @ValueSource(strings={"missing","wrong-shape","missing-id","invalid-id","empty","outside"})
    void documentedGroupFailuresFailClosed(String mode)throws Exception{
        PROVIDER.groups=switch(mode){case "missing"->null;case "wrong-shape"->"wrong";case "missing-id"->List.of(Map.of("name","Members"));case "invalid-id"->List.of(Map.of("id","invalid"));case "empty"->List.of();default->List.of(Map.of("id",UUID.randomUUID().toString(),"name","Members"));};
        assertEquals("https://kelios.example/auth/failed",loginOidc().getResponse().getHeader("Location"));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM external_identity",Integer.class));
    }
    @Test void emptyAllowlistAndChangedConfigurationFailClosed()throws Exception{
        var flow=flow();configure(tenant,0L,Set.of());assertEquals("https://kelios.example/auth/failed",callback(flow,flow.authorization().state()).getResponse().getHeader("Location"));
        assertEquals("https://kelios.example/auth/failed",loginOidc().getResponse().getHeader("Location"));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM external_identity",Integer.class));
    }
    @Test void stateCallbackTenantReplayAndFlowExpiry()throws Exception{
        var flow=flow();assertEquals("https://kelios.example/auth/failed",callback(flow,"incorrect-state").getResponse().getHeader("Location"));
        var wrongTenant=mvc.perform(get("/api/v1/auth/oidc/"+other+"/callback").cookie(cookie(flow.start(),BrowserTokens.FLOW)).param("state",flow.authorization().state()).param("code",flow.authorization().code())).andReturn();assertEquals("https://kelios.example/auth/failed",wrongTenant.getResponse().getHeader("Location"));
        assertEquals("https://kelios.example/auth/complete",callback(flow,flow.authorization().state()).getResponse().getHeader("Location"));assertEquals("https://kelios.example/auth/failed",callback(flow,flow.authorization().state()).getResponse().getHeader("Location"));
        var expired=flow();CLOCK.now.updateAndGet(i->i.plusSeconds(301));assertEquals("https://kelios.example/auth/failed",callback(expired,expired.authorization().state()).getResponse().getHeader("Location"));
    }
    @Test void discoveryAndPkceFailuresAndNoHardcodedProvider(){
        PROVIDER.mode="discovery-issuer";assertThrows(AuthFailure.class,()->oidc.start(tenant));PROVIDER.mode="pkce-unsupported";assertThrows(AuthFailure.class,()->oidc.start(tenant));
        assertThrows(AuthFailure.class,()->configurations.put(admin,tenant,"http://localhost","client","secret",true,0L,Set.of(PROVIDER.group)));
        configure(other,null,Set.of(PROVIDER.group));assertEquals(other,configurations.provider(other).tenantId());
    }
    @Test void jitRaceNoOrphansAndNoCrossTenantMapping()throws Exception{
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->provisioning.complete(tenant,0,PROVIDER.issuer,"fixture-client","concurrent-subject","Person",Set.of(PROVIDER.group)));
            var second=pool.submit(()->provisioning.complete(tenant,0,PROVIDER.issuer,"fixture-client","concurrent-subject","Person",Set.of(PROVIDER.group)));
            assertEquals(first.get(20,TimeUnit.SECONDS).user().id(),second.get(20,TimeUnit.SECONDS).user().id());
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM app_user WHERE scope='TENANT'",Integer.class));configure(other,null,Set.of(PROVIDER.group));
        assertThrows(AuthFailure.class,()->provisioning.complete(other,0,PROVIDER.issuer,"fixture-client","concurrent-subject","Person",Set.of(PROVIDER.group)));
    }
    @Test void laterGroupRemovalDoesNotSynchronizeOrBlockRefresh()throws Exception{
        var original=auth.refresh(cookie(loginOidc(),BrowserTokens.REFRESH).getValue());auth.approve(admin,original.user().id());configure(tenant,0L,Set.of());PROVIDER.groups=List.of();
        assertEquals(Status.ACTIVE,auth.refresh(original.refreshToken()).user().status());assertEquals("https://kelios.example/auth/failed",loginOidc().getResponse().getHeader("Location"));
        auth.disable(admin,original.user().id());assertThrows(AuthFailure.class,()->auth.refresh(original.refreshToken()));
    }
    @Test void tenantAdminApprovalLimitsAndReadOnlyRoles(){
        var tenantAdmin=fixture(Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE);var pending=fixture(Scope.TENANT,tenant,Role.USER,Status.PENDING);var otherPending=fixture(Scope.TENANT,other,Role.USER,Status.PENDING);var adminPending=fixture(Scope.TENANT,tenant,Role.ADMIN,Status.PENDING);
        assertEquals(Status.ACTIVE,auth.approve(tenantAdmin,pending.id()).status());assertThrows(AuthFailure.class,()->auth.approve(tenantAdmin,otherPending.id()));assertThrows(AuthFailure.class,()->auth.approve(tenantAdmin,adminPending.id()));assertThrows(AuthFailure.class,()->auth.role(tenantAdmin,pending.id(),Role.ADMIN));
        assertThrows(AuthFailure.class,()->auth.disable(tenantAdmin,adminPending.id()));assertTrue(auth.list(tenantAdmin,new com.github.ksewen.kelios.identity.UserQuery(0,100,null,null,null,null,null,null,null)).items().stream().allMatch(u->u.tenant().id().equals(tenant) && u.role()==Role.USER));
        for(var reader:List.of(fixture(Scope.GLOBAL,null,Role.USER,Status.ACTIVE),fixture(Scope.TENANT,tenant,Role.USER,Status.ACTIVE)))assertThrows(AuthFailure.class,()->auth.approve(reader,otherPending.id()));
    }
    @Test void currentDatabaseRefreshRoleAndStatusAndAbsoluteExpiry(){
        var user=auth.createGlobal(admin,"refresh-user@example.org","Refresh","CorrectHorse25!Battery");var original=auth.login("refresh-user@example.org","CorrectHorse25!Battery");auth.role(admin,user.id(),Role.ADMIN);
        var current=auth.refresh(original.refreshToken());assertEquals(Role.ADMIN,current.user().role());assertEquals(Role.USER,tokens.verify(original.accessToken(),"access").identity().role());
        CLOCK.now.updateAndGet(i->i.plus(Duration.ofDays(30)).plusSeconds(30));assertThrows(AuthFailure.class,()->auth.refresh(original.refreshToken()));
    }
    @Test void scopedServiceBoundariesAndServerActorsPreserveDomain(){
        principal(admin);UUID loc=secured.createPlace(PlaceService.Kind.LOCATION,tenant,"Room"),category=secured.category(null,"Type"),manufacturer=secured.manufacturer(null,"Manufacturer");
        var asset=secured.register(tenant,new AssetService.Registration(new AssetService.DirectSelection(category,manufacturer),loc,null,false,null,null,null));
        var tenantReader=fixture(Scope.TENANT,tenant,Role.USER,Status.ACTIVE);principal(tenantReader);assertEquals(asset.id(),secured.asset(asset.id()).id());assertThrows(AuthFailure.class,()->secured.assetText(asset.id(),0,null,null,"illegal"));
        principal(fixture(Scope.TENANT,other,Role.ADMIN,Status.ACTIVE));assertEquals("NOT_FOUND",assertThrows(com.github.ksewen.kelios.platform.BusinessFailure.class,()->secured.asset(asset.id())).code());assertEquals("NOT_FOUND",assertThrows(com.github.ksewen.kelios.platform.BusinessFailure.class,()->secured.deleteAsset(asset.id(),0)).code());
        principal(fixture(Scope.GLOBAL,null,Role.USER,Status.ACTIVE));assertEquals(asset.id(),secured.asset(asset.id()).id());assertThrows(AuthFailure.class,()->secured.publish(CatalogService.Kind.CATEGORY,category));
        principal(fixture(Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE));assertThrows(AuthFailure.class,()->secured.editLabel(CatalogService.Kind.CATEGORY,category,"No global write"));
        assertEquals(admin.id(),jdbc.queryForObject("SELECT actor_id FROM asset_event WHERE asset_id=?",UUID.class,asset.id()));
    }
    @Test void postgresIdentityCompositeConstraintsAndUpgradePreserveFoundation(){
        var user=fixture(Scope.TENANT,tenant,Role.USER,Status.PENDING);
        assertThrows(RuntimeException.class,()->tx(()->{jdbc.update("INSERT INTO external_identity(issuer,subject,user_id,tenant_id) VALUES('https://example.test','wrong',?,?)",user.id(),other);return null;}));
        Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas("phase2_upgrade").defaultSchema("phase2_upgrade").target("3").load().migrate();
        UUID retained=UUID.randomUUID();jdbc.update("INSERT INTO phase2_upgrade.app_user(id,scope,role,status,display_name) VALUES(?,'GLOBAL','USER','ACTIVE','Retained')",retained);
        Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas("phase2_upgrade").defaultSchema("phase2_upgrade").target("4").load().migrate();
        assertEquals("Retained",jdbc.queryForObject("SELECT display_name FROM phase2_upgrade.app_user WHERE id=?",String.class,retained));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM phase2_upgrade.local_global_credential",Integer.class));
    }
    @Test void disableWinsAgainstWaitingRefreshUsingIndependentConnection()throws Exception{
        var original=auth.login("initial-admin@example.org","CorrectHorse25!Battery");var ready=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var change=pool.submit(()->tx(()->{jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",UUID.class,admin.id());ready.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=?",admin.id());return null;}));
            assertTrue(ready.await(10,TimeUnit.SECONDS));var refresh=pool.submit(()->{assertThrows(AuthFailure.class,()->auth.refresh(original.refreshToken()));return null;});
            Thread.sleep(150);release.countDown();change.get(15,TimeUnit.SECONDS);refresh.get(15,TimeUnit.SECONDS);
        }
    }
    void awaitDatabaseWait() throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline){
            if(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",Integer.class)>0)return;
            Thread.sleep(20);
        }
        fail("Expected an independent PostgreSQL connection waiting for a row lock");
    }
    @Test void credentialResetWinsAgainstLoginFromOldHash()throws Exception{
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var reset=pool.submit(()->tx(()->{auth.resetCredential(admin,admin.id(),"initial-admin@example.org","ReplacementHorse25!Battery");locked.countDown();try{assertTrue(release.await(15,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}return null;}));
            assertTrue(locked.await(10,TimeUnit.SECONDS));
            var login=pool.submit(()->assertThrows(AuthFailure.class,()->auth.login("initial-admin@example.org","CorrectHorse25!Battery")));
            try{awaitDatabaseWait();}finally{release.countDown();}
            reset.get(20,TimeUnit.SECONDS);assertEquals(401,login.get(20,TimeUnit.SECONDS).status());
        }
        assertDoesNotThrow(()->auth.login("initial-admin@example.org","ReplacementHorse25!Battery"));
    }
    @Test void recoverySerializesWithAdminPromotion()throws Exception{
        var candidate=auth.createGlobal(admin,"promoted-admin@example.org","Promoted","CorrectHorse25!Battery");jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=?",admin.id());
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var promotion=pool.submit(()->tx(()->{auth.role(admin,candidate.id(),Role.ADMIN);locked.countDown();try{assertTrue(release.await(15,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}return null;}));
            assertTrue(locked.await(10,TimeUnit.SECONDS));var recovery=pool.submit(()->assertThrows(AuthFailure.class,()->auth.bootstrap("blocked-recovery@example.org","Recovery","CorrectHorse25!Battery")));
            try{awaitDatabaseWait();}finally{release.countDown();}
            promotion.get(20,TimeUnit.SECONDS);assertEquals(409,recovery.get(20,TimeUnit.SECONDS).status());
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM app_user WHERE scope='GLOBAL' AND role='ADMIN' AND status='ACTIVE'",Integer.class));
    }
    @Test void configurationChangeWinsAgainstWaitingJit()throws Exception{
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var configuration=pool.submit(()->tx(()->{configure(tenant,0L,Set.of());locked.countDown();try{assertTrue(release.await(15,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}return null;}));
            assertTrue(locked.await(10,TimeUnit.SECONDS));var jit=pool.submit(()->assertThrows(AuthFailure.class,()->provisioning.complete(tenant,0,PROVIDER.issuer,"fixture-client","waiting-subject","Person",Set.of(PROVIDER.group))));
            try{awaitDatabaseWait();}finally{release.countDown();}
            configuration.get(20,TimeUnit.SECONDS);assertEquals(409,jit.get(20,TimeUnit.SECONDS).status());
        }
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM external_identity",Integer.class));
    }
    @Test void publicationWinsAgainstTenantCatalogEdit()throws Exception{
        UUID category=catalog.createCategory(tenant,"Before publication",admin.actor());var tenantAdmin=fixture(Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE);
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var publish=pool.submit(()->tx(()->{catalog.publish(CatalogService.Kind.CATEGORY,category,admin.actor());locked.countDown();try{assertTrue(release.await(15,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}return null;}));
            assertTrue(locked.await(10,TimeUnit.SECONDS));var edit=pool.submit(()->{principal(tenantAdmin);try{return assertThrows(AuthFailure.class,()->secured.editLabel(CatalogService.Kind.CATEGORY,category,"Unauthorized"));}finally{SecurityContextHolder.clearContext();}});
            try{awaitDatabaseWait();}finally{release.countDown();}
            publish.get(20,TimeUnit.SECONDS);assertEquals(403,edit.get(20,TimeUnit.SECONDS).status());
        }
        assertEquals("Before publication",catalog.read(CatalogService.Kind.CATEGORY,category).name());
    }
    @Test void administrationHttpMatrixAndAuthoritativeTargets()throws Exception{
        for(var actor:List.of(admin,fixture(Scope.GLOBAL,null,Role.USER,Status.ACTIVE),fixture(Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE),fixture(Scope.TENANT,tenant,Role.USER,Status.ACTIVE))){
            String bearer=tokens.login(actor).accessToken();
            var list=mvc.perform(get("/api/v1/tenants").header("Authorization","Bearer "+bearer)).andReturn();assertEquals(actor.scope()==Scope.GLOBAL?200:403,list.getResponse().getStatus());
            assertEquals(actor.scope()==Scope.GLOBAL || actor.role()==Role.ADMIN?200:403,mvc.perform(get("/api/v1/tenants/"+tenant).header("Authorization","Bearer "+bearer)).andReturn().getResponse().getStatus());
            assertEquals(actor.globalAdmin()?200:403,mvc.perform(post("/api/v1/tenants").header("Authorization","Bearer "+bearer).contentType("application/json").content("{\"name\":\"Authorized creation\"}")).andReturn().getResponse().getStatus());
            assertEquals(actor.role()==Role.ADMIN?200:403,mvc.perform(get("/api/v1/tenants/"+tenant+"/oidc").header("Authorization","Bearer "+bearer)).andReturn().getResponse().getStatus());
        }
    }
    @Test void providerTransportRejectsUntrustedHostsAndPrivateAddresses()throws Exception{
        var environment=new org.springframework.mock.env.MockEnvironment().withProperty("kelios.auth.provider-trust-store",PROVIDER.trustStore.toAbsolutePath().toString()).withProperty("kelios.auth.provider-trust-store-password","fixture-store");
        try(var http=new ProviderHttp(new AuthSettings(environment))){
            assertThrows(AuthFailure.class,()->http.get(PROVIDER.issuer+"/.well-known/openid-configuration",PROVIDER.issuer,null));
            assertThrows(AuthFailure.class,()->http.endpoint("https://attacker.invalid/token",PROVIDER.issuer));
            assertThrows(AuthFailure.class,()->http.endpoint("http://localhost/token",PROVIDER.issuer));
            assertThrows(AuthFailure.class,()->http.endpoint("https://user@localhost/token",PROVIDER.issuer));
        }
        for(String address:List.of("127.0.0.1","10.0.0.1","169.254.169.254","100.64.0.1","::1","fc00::1"))assertTrue(ProviderHttp.blocked(java.net.InetAddress.getByName(address)));
    }

    @Test void subjectAndIssuerChangesCreateDistinctPendingIdentities(){
        var first=provisioning.complete(tenant,0,PROVIDER.issuer,"fixture-client","first-subject","Person",Set.of(PROVIDER.group));
        var second=provisioning.complete(tenant,0,PROVIDER.issuer,"fixture-client","other-subject","Person",Set.of(PROVIDER.group));
        assertNotEquals(first.user().id(),second.user().id());
        String changed="https://other-issuer.example";configurations.put(admin,tenant,changed,"fixture-client",null,true,0L,Set.of(PROVIDER.group));
        var third=provisioning.complete(tenant,1,changed,"fixture-client","first-subject","Person",Set.of(PROVIDER.group));
        assertNotEquals(first.user().id(),third.user().id());assertEquals(Status.PENDING,third.user().status());
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM external_identity",Integer.class));
        jdbc.update("DELETE FROM external_identity WHERE user_id=?",third.user().id());jdbc.update("DELETE FROM app_user WHERE id=?",third.user().id());
        assertThrows(AuthFailure.class,()->auth.refresh(third.refreshToken()));
    }
    @Test void processLocalThrottleHasBoundedCapacityAndTimeWindow(){
        var limit=new LoginThrottle(CLOCK);for(int i=0;i<10;i++)limit.check("account",10);
        assertEquals(429,assertThrows(AuthFailure.class,()->limit.check("account",10)).status());
        CLOCK.now.updateAndGet(i->i.plusSeconds(60));assertDoesNotThrow(()->limit.check("account",10));
        for(int i=0;i<4095;i++)limit.check("key-"+i,60);
        assertEquals(429,assertThrows(AuthFailure.class,()->limit.check("overflow",60)).status());
        CLOCK.now.updateAndGet(i->i.plusSeconds(60));assertDoesNotThrow(()->limit.check("overflow",60));
    }

    @Test void oversizedIdentityBodyIsRejectedBeforeJsonAndHashing()throws Exception{
        var result=unsafe(post("/api/v1/auth/local/login").contentType("application/json").content("x".repeat(32769)),null);
        assertEquals(400,result.getResponse().getStatus());assertEquals("application/problem+json",result.getResponse().getContentType());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM local_global_credential",Integer.class));
    }

    @Test void userAdministrationRejectsReadersBeforeTargetLookupAndScopesTenantTargets(){
        var reader=fixture(Scope.GLOBAL,null,Role.USER,Status.ACTIVE);var tenantAdmin=fixture(Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE);
        var crossTenant=fixture(Scope.TENANT,other,Role.USER,Status.PENDING);
        assertEquals(403,assertThrows(AuthFailure.class,()->auth.approve(reader,UUID.randomUUID())).status());
        assertEquals(403,assertThrows(AuthFailure.class,()->auth.disable(reader,UUID.randomUUID())).status());
        assertEquals(404,assertThrows(AuthFailure.class,()->auth.approve(tenantAdmin,crossTenant.id())).status());
        assertEquals(404,assertThrows(AuthFailure.class,()->auth.approve(tenantAdmin,UUID.randomUUID())).status());
    }

    @Test void pendingAndCredentiallessAdminsDoNotCountAsUsable(){
        jdbc.update("UPDATE app_user SET status='PENDING' WHERE id=?",admin.id());
        var recovered=auth.bootstrap("pending-recovery@example.org","Recovered","CorrectHorse25!Battery");
        assertEquals(Status.PENDING,identities.load(admin.id(),"").status());
        jdbc.update("DELETE FROM local_global_credential WHERE user_id=?",recovered.id());
        var again=auth.bootstrap("credentialless-recovery@example.org","Recovered again","CorrectHorse25!Battery");
        assertEquals(Status.ACTIVE,again.status());assertEquals(1,auditCount("GLOBAL_ADMIN_BOOTSTRAPPED",again.id()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM app_user u JOIN local_global_credential c ON c.user_id=u.id WHERE u.scope='GLOBAL' AND u.role='ADMIN' AND u.status='ACTIVE'",Integer.class));
    }
    @Test void adminGrantWinsAgainstWaitingTenantApproval()throws Exception{
        var tenantAdmin=fixture(Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE);var target=fixture(Scope.TENANT,tenant,Role.USER,Status.PENDING);
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var grant=pool.submit(()->tx(()->{auth.role(admin,target.id(),Role.ADMIN);locked.countDown();try{assertTrue(release.await(15,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}return null;}));
            assertTrue(locked.await(10,TimeUnit.SECONDS));var approve=pool.submit(()->assertThrows(AuthFailure.class,()->auth.approve(tenantAdmin,target.id())));
            try{awaitDatabaseWait();}finally{release.countDown();}
            grant.get(20,TimeUnit.SECONDS);assertEquals(404,approve.get(20,TimeUnit.SECONDS).status());
        }
        assertEquals(Role.ADMIN,identities.load(target.id(),"").role());assertEquals(Status.PENDING,identities.load(target.id(),"").status());assertEquals(0,auditCount("USER_APPROVED",target.id()));
    }


    @Test void organizationDiscoveryIsBoundedMinimalAndDoesNotGrantMembership()throws Exception {
        var result=mvc.perform(get("/api/v1/auth/oidc/organizations").secure(true)).andReturn();
        assertEquals(200,result.getResponse().getStatus());
        var directory=body(result);assertEquals(1,((Number)directory.get("totalElements")).intValue());
        assertEquals(0,((Number)directory.get("page")).intValue());assertEquals(20,((Number)directory.get("size")).intValue());
        var item=(Map<?,?>)((List<?>)directory.get("items")).getFirst();
        assertEquals(Set.of("routingId","name"),item.keySet());assertEquals(tenant.toString(),item.get("routingId"));assertEquals("Tenant",item.get("name"));
        assertEquals(0,((Number)body(mvc.perform(get("/api/v1/auth/oidc/organizations").param("q","%").secure(true)).andReturn()).get("totalElements")).intValue());
        assertEquals(400,mvc.perform(get("/api/v1/auth/oidc/organizations").param("size","101").secure(true)).andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(get("/api/v1/auth/oidc/organizations").param("page","-1").secure(true)).andReturn().getResponse().getStatus());
        assertEquals(403,mvc.perform(post("/api/v1/auth/oidc/"+tenant+"/start").secure(true)).andReturn().getResponse().getStatus());
        var completed=loginOidc();var user=auth.refresh(cookie(completed,BrowserTokens.REFRESH).getValue()).user();
        assertEquals(tenant,user.tenantId());assertEquals(Status.PENDING,user.status());assertEquals(Role.USER,user.role());
        configure(other,null,Set.of(PROVIDER.group));
        var first=body(mvc.perform(get("/api/v1/auth/oidc/organizations").param("size","1").secure(true)).andReturn());
        assertEquals(2,((Number)first.get("totalElements")).intValue());assertEquals("Other",((Map<?,?>)((List<?>)first.get("items")).getFirst()).get("name"));
        var second=body(mvc.perform(get("/api/v1/auth/oidc/organizations").param("size","1").param("page","1").secure(true)).andReturn());
        assertEquals("Tenant",((Map<?,?>)((List<?>)second.get("items")).getFirst()).get("name"));
        assertEquals(400,mvc.perform(get("/api/v1/auth/oidc/organizations").param("q","x".repeat(129)).secure(true)).andReturn().getResponse().getStatus());
        assertEquals(403,assertThrows(AuthFailure.class,()->provisioning.complete(other,0,PROVIDER.issuer,"fixture-client",PROVIDER.subject,"Same name",Set.of(PROVIDER.group))).status());
    }
    @Test void organizationDiscoveryOmitsDisabledAndUnconfiguredProviders()throws Exception {
        var p=configurations.get(admin,tenant);configurations.put(admin,tenant,p.issuer(),p.clientId(),null,false,p.version(),p.allowedGroupIds());
        assertEquals(0,((Number)body(mvc.perform(get("/api/v1/auth/oidc/organizations").secure(true)).andReturn()).get("totalElements")).intValue());
        assertNotEquals(200,unsafe(post("/api/v1/auth/oidc/"+tenant+"/start"),null).getResponse().getStatus());
    }

    @ParameterizedTest @ValueSource(strings={"", " \t\n ", "\u2003\u2003"})
    void organizationDiscoveryBlankIsUnfiltered(String query)throws Exception {
        var result=mvc.perform(get("/api/v1/auth/oidc/organizations").param("q",query).secure(true)).andReturn();
        assertEquals(200,result.getResponse().getStatus());var page=body(result);
        assertEquals(1,((Number)page.get("totalElements")).intValue());assertEquals(20,((Number)page.get("size")).intValue());
        assertEquals(tenant.toString(),((Map<?,?>)((List<?>)page.get("items")).getFirst()).get("routingId"));
        assertEquals(1,configurations.organizations(0,20,query).totalElements());
    }
    @Test void organizationDiscoveryTrimmedCaseInsensitiveSearchAndLengthBound()throws Exception {
        var result=mvc.perform(get("/api/v1/auth/oidc/organizations").param("q"," \tTeNaNt\u2003 ").secure(true)).andReturn();
        assertEquals(200,result.getResponse().getStatus());assertEquals(1,((Number)body(result).get("totalElements")).intValue());
        assertEquals(tenant,configurations.organizations(0,20,"  TENANT  ").items().getFirst().routingId());
        for(String query:List.of("x".repeat(128),"  "+"x".repeat(128)+"  ")){
            var boundary=mvc.perform(get("/api/v1/auth/oidc/organizations").param("q",query).secure(true)).andReturn();
            assertEquals(200,boundary.getResponse().getStatus());assertEquals(0,((Number)body(boundary).get("totalElements")).intValue());
        }
        var over=mvc.perform(get("/api/v1/auth/oidc/organizations").param("q","x".repeat(129)).secure(true)).andReturn();
        assertEquals(400,over.getResponse().getStatus());assertEquals(400,((Number)body(over).get("status")).intValue());
        assertEquals(400,assertThrows(AuthFailure.class,()->configurations.organizations(0,20,"x".repeat(129))).status());
        assertEquals(400,assertThrows(AuthFailure.class,()->configurations.organizations(0,20,null)).status());
    }
    @ParameterizedTest @ValueSource(strings={"%", "_", "\\"})
    void organizationDiscoveryWildcardsAreLiteral(String literal)throws Exception {
        String name="Routing marker"+literal+"literal";
        UUID matching=tenants.create(name,admin.actor());configure(matching,null,Set.of(PROVIDER.group));
        UUID decoy=tenants.create("Routing markerXliteral",admin.actor());configure(decoy,null,Set.of(PROVIDER.group));
        var response=mvc.perform(get("/api/v1/auth/oidc/organizations").param("q"," "+literal+" ").secure(true)).andReturn();
        assertEquals(200,response.getResponse().getStatus());var page=body(response);
        assertEquals(1,((Number)page.get("totalElements")).intValue());var item=(Map<?,?>)((List<?>)page.get("items")).getFirst();
        assertEquals(Set.of("routingId","name"),item.keySet());assertEquals(matching.toString(),item.get("routingId"));assertEquals(name,item.get("name"));
    }
    @ParameterizedTest @CsvSource({"-1,20","100001,20","0,0","0,101"})
    void organizationDiscoveryRejectsInvalidPageBounds(int page,int size)throws Exception {
        var result=mvc.perform(get("/api/v1/auth/oidc/organizations").param("page",String.valueOf(page)).param("size",String.valueOf(size)).secure(true)).andReturn();
        assertEquals(400,result.getResponse().getStatus());assertEquals(400,((Number)body(result).get("status")).intValue());
        assertEquals(400,assertThrows(AuthFailure.class,()->configurations.organizations(page,size,"")).status());
    }
    @ParameterizedTest @ValueSource(ints={1,10,20,30,40,50,100})
    void organizationDiscoveryExplicitSizeAndMaximumPage(int size)throws Exception {
        var result=mvc.perform(get("/api/v1/auth/oidc/organizations").param("page","100000").param("size",String.valueOf(size)).secure(true)).andReturn();
        assertEquals(200,result.getResponse().getStatus());var page=body(result);
        assertEquals(100000,((Number)page.get("page")).intValue());assertEquals(size,((Number)page.get("size")).intValue());
        assertEquals(1,((Number)page.get("totalElements")).intValue());assertTrue(((List<?>)page.get("items")).isEmpty());
    }
    @Test void organizationDiscoveryDefaultAndLaterPageHaveScopedFullCountAndMinimalProjection()throws Exception {
        for(int i=0;i<25;i++)configure(tenants.create("Routing fixture "+String.format(Locale.ROOT,"%02d",i),admin.actor()),null,Set.of(PROVIDER.group));
        var disabled=configure(tenants.create("Routing fixture disabled",admin.actor()),null,Set.of(PROVIDER.group));
        configurations.put(admin,disabled.tenantId(),disabled.issuer(),disabled.clientId(),null,false,disabled.version(),disabled.allowedGroupIds());
        tenants.create("Routing fixture unconfigured",admin.actor());
        long usersBefore=jdbc.queryForObject("SELECT count(*) FROM app_user",Long.class),identitiesBefore=jdbc.queryForObject("SELECT count(*) FROM external_identity",Long.class),auditBefore=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
        var seen=new HashSet<String>();
        for(int index=0;index<3;index++){
            var request=get("/api/v1/auth/oidc/organizations").param("q"," Routing fixture ").secure(true);
            if(index!=0)request.param("page",String.valueOf(index));
            var response=mvc.perform(request).andReturn();assertEquals(200,response.getResponse().getStatus());var page=body(response);
            assertEquals(Set.of("items","totalElements","page","size"),page.keySet());assertEquals(25,((Number)page.get("totalElements")).intValue());
            assertEquals(index,((Number)page.get("page")).intValue());assertEquals(20,((Number)page.get("size")).intValue());
            var items=(List<?>)page.get("items");assertEquals(index==0?20:index==1?5:0,items.size());
            for(int n=0;n<items.size();n++){
                var item=(Map<?,?>)items.get(n);assertEquals(Set.of("routingId","name"),item.keySet());
                assertEquals("Routing fixture "+String.format(Locale.ROOT,"%02d",index*20+n),item.get("name"));assertTrue(seen.add((String)item.get("routingId")));
            }
        }
        assertEquals(25,seen.size());assertEquals(usersBefore,jdbc.queryForObject("SELECT count(*) FROM app_user",Long.class));
        assertEquals(identitiesBefore,jdbc.queryForObject("SELECT count(*) FROM external_identity",Long.class));assertEquals(auditBefore,jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class));
    }

    @Test void emailCanonicalUniquenessAndEditPreservesIdentityPasswordAndCreationMetadata(){
        var u=auth.createGlobal(admin," Mixed.Case@EXAMPLE.ORG ","Email owner","Aa1!12345678");
        assertEquals("mixed.case@example.org",auth.detail(admin,u.id()).globalAccount().email());
        assertEquals(CLOCK.instant(),auth.detail(admin,u.id()).globalAccount().emailVerifiedAt());
        var before=jdbc.queryForMap("SELECT * FROM app_user WHERE id=?",u.id());
        String hash=jdbc.queryForObject("SELECT password_hash FROM local_global_credential WHERE user_id=?",String.class,u.id());
        long created=auditCount("USER_CREATED",u.id());
        assertThrows(RuntimeException.class,()->auth.createGlobal(admin,"MIXED.CASE@example.org","Collision","Aa1!12345678"));
        assertThrows(RuntimeException.class,()->auth.email(admin,u.id(),"INITIAL-ADMIN@example.org"));
        assertEquals(before,jdbc.queryForMap("SELECT * FROM app_user WHERE id=?",u.id()));
        CLOCK.now.updateAndGet(t->t.plusSeconds(1));auth.email(admin,u.id()," New.Address@EXAMPLE.ORG ");
        var after=jdbc.queryForMap("SELECT * FROM app_user WHERE id=?",u.id());
        for(String field:List.of("id","created_by","created_at","scope","role","status","display_name"))assertEquals(before.get(field),after.get(field));
        assertEquals(admin.id(),after.get("updated_by"));assertNotEquals(before.get("updated_at"),after.get("updated_at"));
        assertEquals(hash,jdbc.queryForObject("SELECT password_hash FROM local_global_credential WHERE user_id=?",String.class,u.id()));
        assertEquals(u.id(),auth.login("NEW.ADDRESS@EXAMPLE.ORG","Aa1!12345678").user().id());
        assertThrows(AuthFailure.class,()->auth.login("mixed.case@example.org","Aa1!12345678"));
        assertEquals(created,auditCount("USER_CREATED",u.id()));assertEquals(1,auditCount("GLOBAL_EMAIL_CHANGED",u.id()));
        assertEquals(admin.id(),jdbc.queryForObject("SELECT actor_id FROM audit_log WHERE action='GLOBAL_EMAIL_CHANGED' AND target_id=?",UUID.class,u.id()));
        String data=jdbc.queryForObject("SELECT data::text FROM audit_log WHERE action='GLOBAL_EMAIL_CHANGED' AND target_id=?",String.class,u.id());assertFalse(data.contains("@"));
    }
    @ParameterizedTest @CsvSource({"ACTIVE,true,true","ACTIVE,false,false","PENDING,true,false","PENDING,false,false","DISABLED,true,false","DISABLED,false,false"})
    void globalLoginAndRefreshRequireVerificationApprovalAndActive(Status status,boolean verified,boolean allowed){
        var u=auth.createGlobal(admin,"validity@example.org","Validity","Aa1!12345678");var issued=auth.login("validity@example.org","Aa1!12345678");
        jdbc.update("UPDATE app_user SET status=? WHERE id=?",status.name(),u.id());
        if(!verified)jdbc.update("UPDATE local_global_credential SET email_verified_at=NULL WHERE user_id=?",u.id());
        if(allowed){assertDoesNotThrow(()->auth.login("validity@example.org","Aa1!12345678"));assertDoesNotThrow(()->auth.refresh(issued.refreshToken()));}
        else{assertEquals(403,assertThrows(AuthFailure.class,()->auth.login("validity@example.org","Aa1!12345678")).status());assertEquals(403,assertThrows(AuthFailure.class,()->auth.refresh(issued.refreshToken())).status());}
        assertDoesNotThrow(()->tokens.verify(issued.accessToken(),"access"));
    }
    @Test void approvalDoesNotFabricateVerificationAndResetRestoresExplicitManualVerification(){
        var u=auth.createGlobal(admin,"approval@example.org","Approval","Aa1!12345678");jdbc.update("UPDATE app_user SET status='PENDING' WHERE id=?",u.id());jdbc.update("UPDATE local_global_credential SET email_verified_at=NULL WHERE user_id=?",u.id());
        auth.approve(admin,u.id());assertThrows(AuthFailure.class,()->auth.login("approval@example.org","Aa1!12345678"));
        auth.resetCredential(admin,u.id(),"reset@example.org","Bb2!12345678");assertEquals(CLOCK.instant(),auth.detail(admin,u.id()).globalAccount().emailVerifiedAt());assertEquals(u.id(),auth.login("RESET@EXAMPLE.ORG","Bb2!12345678").user().id());
    }
    @Test void unverifiedAdminDoesNotBlockGuardedRecovery(){
        jdbc.update("UPDATE local_global_credential SET email_verified_at=NULL WHERE user_id=?",admin.id());
        var recovered=auth.bootstrap("verified-recovery@example.org","Recovery","Aa1!12345678");assertEquals(CLOCK.instant(),auth.detail(recovered,recovered.id()).globalAccount().emailVerifiedAt());
        assertThrows(AuthFailure.class,()->auth.bootstrap("another@example.org","No","Aa1!12345678"));
    }
    @Test void emailAdministrationRejectsTenantAndOrdinaryUserAndOverposting()throws Exception{
        var u=auth.createGlobal(admin,"email-admin@example.org","User","Aa1!12345678");
        for(Identity denied:List.of(u,fixture(Scope.TENANT,tenant,Role.ADMIN,Status.ACTIVE)))assertEquals(403,assertThrows(AuthFailure.class,()->auth.email(denied,u.id(),"changed@example.org")).status());
        var local=fixture(Scope.TENANT,tenant,Role.USER,Status.ACTIVE);assertEquals(403,assertThrows(AuthFailure.class,()->auth.email(admin,local.id(),"tenant@example.org")).status());
        String bearer=tokens.login(admin).accessToken();
        for(Map<String,Object> body:List.of(Map.<String,Object>of("email","changed@example.org","emailVerifiedAt",CLOCK.instant().toString()),Map.<String,Object>of("login","old-login","password","Aa1!12345678")))assertEquals(400,unsafe(put("/api/v1/users/"+u.id()+"/email").contentType("application/json").content(json.writeValueAsString(body)),bearer).getResponse().getStatus());
        assertEquals(200,unsafe(put("/api/v1/users/"+u.id()+"/email").contentType("application/json").content("{\"email\":\"changed@example.org\"}"),bearer).getResponse().getStatus());
        var detail=mvc.perform(get("/api/v1/users/"+u.id()).header("Authorization","Bearer "+bearer)).andReturn();String text=detail.getResponse().getContentAsString();assertTrue(text.contains("changed@example.org"));assertTrue(text.contains("emailVerifiedAt"));for(String forbidden:List.of("password","hash","loginName","issuer","subject"))assertFalse(text.contains(forbidden));
        assertEquals(400,unsafe(post("/api/v1/auth/local/login").contentType("application/json").content("{\"login\":\"initial-admin\",\"password\":\"Aa1!12345678\"}"),null).getResponse().getStatus());
    }
    @Test void emailEditAuditFailureRollsBackCredentialVerificationAndMetadata(){
        var before=jdbc.queryForMap("SELECT * FROM local_global_credential WHERE user_id=?",admin.id());var metadata=jdbc.queryForMap("SELECT * FROM app_user WHERE id=?",admin.id());
        jdbc.execute("CREATE FUNCTION i1_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='GLOBAL_EMAIL_CHANGED' THEN RAISE EXCEPTION 'isolated audit failure'; END IF; RETURN NEW; END; $$");jdbc.execute("CREATE TRIGGER i1_reject_audit BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION i1_reject_audit()");
        try{assertThrows(RuntimeException.class,()->auth.email(admin,admin.id(),"rollback@example.org"));assertEquals(before,jdbc.queryForMap("SELECT * FROM local_global_credential WHERE user_id=?",admin.id()));assertEquals(metadata,jdbc.queryForMap("SELECT * FROM app_user WHERE id=?",admin.id()));assertEquals(0,auditCount("GLOBAL_EMAIL_CHANGED",admin.id()));}
        finally{jdbc.execute("DROP TRIGGER i1_reject_audit ON audit_log");jdbc.execute("DROP FUNCTION i1_reject_audit()");}
    }
    @Test void emailMigrationCleanTargetAndPopulatedLegacyRefusalWithoutProvenance(){
        String schema="i1_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas(schema).defaultSchema(schema).target("7").load().migrate();
        UUID user=UUID.randomUUID();jdbc.update("INSERT INTO "+schema+".app_user(id,scope,role,status,display_name,created_by,updated_by,created_at,updated_at) VALUES(?,'GLOBAL','ADMIN','ACTIVE','Legacy',?,?,now(),now())",user,user,user);
        jdbc.update("INSERT INTO "+schema+".local_global_credential(user_id,login_name,password_hash) VALUES(?,'legacy','fixture')",user);
        var migration=Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas(schema).defaultSchema(schema).load();assertThrows(RuntimeException.class,migration::migrate);
        assertEquals("legacy",jdbc.queryForObject("SELECT login_name FROM "+schema+".local_global_credential WHERE user_id=?",String.class,user));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND table_name='local_global_credential' AND column_name='email'",Integer.class,schema));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='local_global_credential' AND column_name='login_name'",Integer.class));
        assertEquals(8,jdbc.queryForObject("SELECT count(*) FROM public.flyway_schema_history WHERE success",Integer.class));
    }

    @Test void emailEditWinsAgainstLoginUsingOldEmailEvenWhenPasswordIsUnchanged()throws Exception{
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var edit=pool.submit(()->tx(()->{auth.email(admin,admin.id(),"changed-admin@example.org");locked.countDown();try{assertTrue(release.await(15,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}return null;}));
            assertTrue(locked.await(10,TimeUnit.SECONDS));var login=pool.submit(()->assertThrows(AuthFailure.class,()->auth.login("initial-admin@example.org","CorrectHorse25!Battery")));
            try{awaitDatabaseWait();}finally{release.countDown();}
            edit.get(20,TimeUnit.SECONDS);assertEquals(401,login.get(20,TimeUnit.SECONDS).status());
        }
        assertDoesNotThrow(()->auth.login("changed-admin@example.org","CorrectHorse25!Battery"));
    }

    @Test void callbackRejectsDeletedCurrentConfigurationAndRecreatedValidFlowMayComplete()throws Exception{
        var started=flow();configurations.delete(admin,tenant,0);
        var failed=callback(started,started.authorization().state());assertEquals("https://kelios.example/auth/failed",failed.getResponse().getHeader("Location"));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM external_identity",Integer.class));assertThrows(AuthFailure.class,()->oidc.start(tenant));
        configure(tenant,null,Set.of(PROVIDER.group));var valid=flow();configurations.delete(admin,tenant,0);configure(tenant,null,Set.of(PROVIDER.group));
        var completed=callback(valid,valid.authorization().state());assertEquals("https://kelios.example/auth/complete",completed.getResponse().getHeader("Location"));var pair=auth.refresh(cookie(completed,BrowserTokens.REFRESH).getValue());UUID id=pair.user().id();assertEquals(Status.PENDING,pair.user().status());auth.approve(admin,id);
        var active=auth.refresh(pair.refreshToken());configurations.delete(admin,tenant,0);assertEquals(id,auth.refresh(active.refreshToken()).user().id());assertDoesNotThrow(()->tokens.verify(active.accessToken(),"access"));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM external_identity WHERE user_id=?",Integer.class,id));
    }
    @Test void callbackRejectsCurrentRecreationThatDoesNotSatisfyClientChecks()throws Exception{
        var started=flow();configurations.delete(admin,tenant,0);configurations.put(admin,tenant,PROVIDER.issuer,"different-client","fixture-secret",true,null,Set.of(PROVIDER.group));assertEquals("https://kelios.example/auth/failed",callback(started,started.authorization().state()).getResponse().getHeader("Location"));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM external_identity",Integer.class));
    }
}
