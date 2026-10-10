package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.identity.UserService.*;
import com.github.ksewen.kelios.platform.security.BrowserTokens;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import jakarta.servlet.http.Cookie;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class JwtAndBrowserTest {
    static AuthTestKeys keys;JwtTokens tokens;Clock clock=Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"),ZoneOffset.UTC);
    Identity user=new Identity(UUID.randomUUID(),Scope.GLOBAL,null,Role.ADMIN,Status.ACTIVE,"Administrator");
    @BeforeAll static void keys()throws Exception{keys=new AuthTestKeys("jwt-test-keys");}
    @BeforeEach void setup(){tokens=new JwtTokens(keys.settings(),clock);}
    @Test void signedTypesClaimsLifetimeAndPurpose(){
        var pair=tokens.login(user);var access=tokens.verify(pair.accessToken(),"access");var refresh=tokens.verify(pair.refreshToken(),"refresh");
        assertEquals(user,access.identity());assertNull(refresh.identity());assertEquals(clock.instant().plusSeconds(900),access.expiresAt());assertEquals(clock.instant().plus(Duration.ofDays(30)),refresh.expiresAt());
        assertThrows(AuthFailure.class,()->tokens.verify(pair.refreshToken(),"access"));assertThrows(AuthFailure.class,()->tokens.verify(pair.accessToken(),"refresh"));
        assertThrows(AuthFailure.class,()->tokens.verify(pair.accessToken()+"broken","access"));assertThrows(AuthFailure.class,()->tokens.verify("malformed","access"));
    }
    @Test void expiryAndSkew(){var pair=tokens.login(user);
        assertDoesNotThrow(()->new JwtTokens(keys.settings(),Clock.offset(clock,Duration.ofSeconds(929))).verify(pair.accessToken(),"access"));
        assertThrows(AuthFailure.class,()->new JwtTokens(keys.settings(),Clock.offset(clock,Duration.ofSeconds(930))).verify(pair.accessToken(),"access"));
        assertThrows(AuthFailure.class,()->new JwtTokens(keys.settings(),Clock.offset(clock,Duration.ofSeconds(-31))).verify(pair.accessToken(),"access"));
    }
    @ParameterizedTest(name="invalid signed claim {0}") @ValueSource(strings={"issuer","audience","purpose","scope","tenant","role","status","future-iat","future-nbf","missing-sub","missing-jti"})
    void rejectsForgedOrInconsistentSignedClaims(String mutation)throws Exception{
        var c=SignedJWT.parse(tokens.login(user).accessToken()).getJWTClaimsSet();var b=new JWTClaimsSet.Builder(c);
        switch(mutation){
            case "issuer"->b.issuer("https://other.example");case "audience"->b.audience("other");case "purpose"->b.claim("token_use","refresh");case "scope"->b.claim("scope",null);
            case "tenant"->b.claim("tenantId",UUID.randomUUID().toString());case "role"->b.claim("role","SUPERADMIN");case "status"->b.claim("status","DISABLED");
            case "future-iat"->b.issueTime(Date.from(clock.instant().plusSeconds(31)));case "future-nbf"->b.notBeforeTime(Date.from(clock.instant().plusSeconds(31)));case "missing-sub"->b.subject(null);case "missing-jti"->b.jwtID(null);
        }
        SignedJWT jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("main").type(new JOSEObjectType("kelios-access+jwt")).build(),b.build());jwt.sign(new RSASSASigner((RSAPrivateKey)keys.pair.getPrivate()));
        assertThrows(AuthFailure.class,()->tokens.verify(jwt.serialize(),"access"));
    }
    @Test void wrongSigningKeyAndAlgorithmRejected()throws Exception{
        var pair=tokens.login(user);var c=SignedJWT.parse(pair.accessToken()).getJWTClaimsSet();
        var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(3072);var rogue=generator.generateKeyPair();
        SignedJWT jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("main").type(new JOSEObjectType("kelios-access+jwt")).build(),c);jwt.sign(new RSASSASigner((RSAPrivateKey)rogue.getPrivate()));assertThrows(AuthFailure.class,()->tokens.verify(jwt.serialize(),"access"));
        SignedJWT unknown=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("unknown").type(new JOSEObjectType("kelios-access+jwt")).build(),c);unknown.sign(new RSASSASigner((RSAPrivateKey)keys.pair.getPrivate()));assertThrows(AuthFailure.class,()->tokens.verify(unknown.serialize(),"access"));
        SignedJWT wrong=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS512).keyID("main").type(new JOSEObjectType("kelios-access+jwt")).build(),c);wrong.sign(new RSASSASigner((RSAPrivateKey)keys.pair.getPrivate()));assertThrows(AuthFailure.class,()->tokens.verify(wrong.serialize(),"access"));
    }
    @Test void encryptedSecretPurposeAndTamperProtection(){
        var secrets=new SecretEnvelope(keys.settings());String envelope=secrets.encrypt("fixture-secret","tenant:one");assertFalse(envelope.contains("fixture-secret"));
        assertTrue(secrets.decrypt(envelope,"tenant:one").equals("fixture-secret"));assertThrows(AuthFailure.class,()->secrets.decrypt(envelope,"tenant:two"));assertThrows(AuthFailure.class,()->secrets.decrypt(envelope+"x","tenant:one"));
    }
    @Test void browserCookiesCsrfOriginBindingAndLogout(){
        var browser=new BrowserTokens(new SecretEnvelope(keys.settings()),keys.settings(),tokens,clock);var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
        String proof=browser.csrf(request,response);String header=response.getHeader("Set-Cookie");assertTrue(header.contains("Secure") && header.contains("HttpOnly") && header.contains("SameSite=Strict") && !header.contains("Domain="));
        request.setCookies(new Cookie(BrowserTokens.CSRF,proof));request.addHeader("Origin","https://kelios.example");request.addHeader("X-CSRF-Token",proof);browser.requireCsrf(request);
        request.removeHeader("Origin");request.addHeader("Origin","https://evil.example");assertThrows(AuthFailure.class,()->browser.requireCsrf(request));
        request.removeHeader("Origin");request.addHeader("Origin","https://kelios.example");request.setCookies(new Cookie(BrowserTokens.CSRF,proof),new Cookie(BrowserTokens.REFRESH,tokens.login(user).refreshToken()));assertThrows(AuthFailure.class,()->browser.requireCsrf(request));
        var cleared=new MockHttpServletResponse();browser.clear(cleared);assertEquals(3,cleared.getHeaders("Set-Cookie").size());assertTrue(cleared.getHeaders("Set-Cookie").stream().allMatch(x->x.contains("Max-Age=0")));
    }
}
