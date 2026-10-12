package com.github.ksewen.kelios.identity.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OidcLogin {
    public record Start(String authorizationUrl,String flowCookie) { @Override public String toString(){return "OidcStart[redacted]";} }
    public record Flow(UUID tenant,long version,String issuer,String clientId,String redirect,String state,String nonce,String verifier,long expiresAt) { @Override public String toString(){return "OidcFlow[redacted]";} }
    private final OidcConfigurationService configs;private final ProviderHttp http;private final SecretEnvelope secrets;private final AuthSettings settings;
    private final OidcProvisioning provisioning;private final Clock clock;private final SecureRandom random=new SecureRandom();private final JsonMapper json=JsonMapper.builder().build();
    public OidcLogin(OidcConfigurationService configs,ProviderHttp http,SecretEnvelope secrets,AuthSettings settings,OidcProvisioning provisioning,Clock clock){this.configs=configs;this.http=http;this.secrets=secrets;this.settings=settings;this.provisioning=provisioning;this.clock=clock;}
    private record ProviderKey(UUID tenant,long version,String issuer,String clientId) {}
    private record Metadata(Instant expires,Map<String,Object> document) {}
    private record Keys(Instant expires,JWKSet set) {}
    private final Map<ProviderKey,Metadata> metadataCache=new LinkedHashMap<>();
    private final Map<ProviderKey,Keys> keyCache=new LinkedHashMap<>();
    private String random(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private String flowContext(){return "oidc-flow:"+settings.publicOrigin();}
    private String text(Map<String,Object> map,String key){if(!(map.get(key) instanceof String value) || value.isBlank() || value.length()>8192)throw AuthFailure.provider();return value;}
    private Map<String,Object> discovery(OidcConfigurationService.Provider provider){
        String issuer=provider.issuer();var key=new ProviderKey(provider.tenantId(),provider.version(),issuer,provider.clientId());
        synchronized(metadataCache){var cached=metadataCache.get(key);if(cached!=null && clock.instant().isBefore(cached.expires()))return cached.document();}
        var doc=http.get(issuer.replaceAll("/$","")+"/.well-known/openid-configuration",issuer,null);
        if(!issuer.equals(doc.get("issuer")) || !(doc.get("id_token_signing_alg_values_supported") instanceof List<?> algorithms) || !algorithms.contains("RS256")
            || !(doc.get("response_types_supported") instanceof List<?> responses) || !responses.contains("code"))throw AuthFailure.provider();
        for(String field:List.of("authorization_endpoint","token_endpoint","userinfo_endpoint","jwks_uri"))http.endpoint(text(doc,field),issuer);
        Object methods=doc.get("code_challenge_methods_supported");if(methods!=null && (!(methods instanceof List<?> list) || !list.contains("S256")))throw AuthFailure.provider();
        synchronized(metadataCache){metadataCache.put(key,new Metadata(clock.instant().plusSeconds(60),doc));if(metadataCache.size()>128)metadataCache.remove(metadataCache.keySet().iterator().next());}
        return doc;
    }
    private JWKSet keys(Map<String,Object> doc,Flow flow)throws Exception{
        var key=new ProviderKey(flow.tenant(),flow.version(),flow.issuer(),flow.clientId());
        synchronized(keyCache){var cached=keyCache.get(key);if(cached!=null && clock.instant().isBefore(cached.expires()))return cached.set();}
        var set=JWKSet.parse(json.writeValueAsString(http.get(text(doc,"jwks_uri"),flow.issuer(),null)));
        synchronized(keyCache){keyCache.put(key,new Keys(clock.instant().plusSeconds(60),set));if(keyCache.size()>128)keyCache.remove(keyCache.keySet().iterator().next());}
        return set;
    }
    public Start start(UUID tenant){
        var provider=configs.provider(tenant);var doc=discovery(provider);
        Flow flow=new Flow(tenant,provider.version(),provider.issuer(),provider.clientId(),settings.callback(tenant),random(),random(),random(),clock.instant().plusSeconds(300).getEpochSecond());
        String cookie=secrets.encrypt(json.writeValueAsString(flow),flowContext());if(cookie.length()>3800)throw new AuthFailure(503,"Provider flow exceeds safe cookie size");
        String challenge;
        try{challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(flow.verifier().getBytes(StandardCharsets.US_ASCII)));}catch(Exception ex){throw AuthFailure.provider();}
        String authorization=text(doc,"authorization_endpoint");
        String url=authorization+(authorization.contains("?")?"&":"?")+"response_type=code&client_id="+encode(flow.clientId())+"&redirect_uri="+encode(flow.redirect())
            +"&scope="+encode("openid profile iserv:groups")+"&state="+encode(flow.state())+"&nonce="+encode(flow.nonce())+"&code_challenge_method=S256&code_challenge="+encode(challenge);
        return new Start(url,cookie);
    }
    public JwtTokens.Pair callback(UUID tenant,String cookie,String state,String code,String responseIssuer){
        try{
            Flow flow=json.readValue(secrets.decrypt(cookie,flowContext()),Flow.class);
            if(!tenant.equals(flow.tenant()) || state==null || !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),flow.state().getBytes(StandardCharsets.UTF_8))
                || clock.instant().getEpochSecond()>=flow.expiresAt() || flow.expiresAt()>clock.instant().plusSeconds(300).getEpochSecond() || code==null || code.isBlank() || code.length()>4096
                || (responseIssuer!=null && !responseIssuer.equals(flow.issuer())))throw AuthFailure.invalid();
            var provider=configs.provider(tenant);
            if(provider.version()!=flow.version() || !provider.issuer().equals(flow.issuer()) || !provider.clientId().equals(flow.clientId()) || !settings.callback(tenant).equals(flow.redirect()))throw new AuthFailure(409,"Provider configuration changed during login");
            var doc=discovery(provider);
            String body="grant_type=authorization_code&code="+encode(code)+"&redirect_uri="+encode(flow.redirect())+"&code_verifier="+encode(flow.verifier());
            Object rawMethods=doc.get("token_endpoint_auth_methods_supported");List<?> methods=rawMethods==null?List.of("client_secret_basic"):(rawMethods instanceof List<?> list?list:List.of());
            String basic=null,secret=configs.secret(provider);
            if(methods.contains("client_secret_basic"))basic=Base64.getEncoder().encodeToString((encode(provider.clientId())+":"+encode(secret)).getBytes(StandardCharsets.US_ASCII));
            else if(methods.contains("client_secret_post"))body+="&client_id="+encode(provider.clientId())+"&client_secret="+encode(secret);
            else throw AuthFailure.provider();
            var response=http.post(text(doc,"token_endpoint"),provider.issuer(),body,basic);
            if(!"Bearer".equalsIgnoreCase(text(response,"token_type")))throw AuthFailure.provider();
            var claims=verifyIdToken(text(response,"id_token"),doc,flow);
            var info=http.get(text(doc,"userinfo_endpoint"),provider.issuer(),text(response,"access_token"));
            if(!claims.getSubject().equals(text(info,"sub")))throw AuthFailure.denied();
            String display=info.get("name") instanceof String name?name:null;
            return provisioning.complete(tenant,flow.version(),flow.issuer(),flow.clientId(),claims.getSubject(),display,IServGroups.parse(info));
        }catch(AuthFailure ex){throw ex;}catch(Exception ex){throw AuthFailure.provider();}
    }
    private JWTClaimsSet verifyIdToken(String value,Map<String,Object> doc,Flow flow)throws Exception{
        SignedJWT jwt=SignedJWT.parse(value);var header=jwt.getHeader();
        if(!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || header.getKeyID()==null || header.getJWKURL()!=null || header.getX509CertURL()!=null || (header.getCriticalParams()!=null && !header.getCriticalParams().isEmpty()))throw AuthFailure.denied();
        var keys=keys(doc,flow);
        if(keys.getKeys().stream().filter(k->header.getKeyID().equals(k.getKeyID())).count()!=1)throw AuthFailure.denied();
        JWK key=keys.getKeyByKeyId(header.getKeyID());
        if(!(key instanceof RSAKey rsa) || (rsa.getKeyUse()!=null && !KeyUse.SIGNATURE.equals(rsa.getKeyUse())) || (rsa.getAlgorithm()!=null && !JWSAlgorithm.RS256.equals(rsa.getAlgorithm()))
            || rsa.toRSAPublicKey().getModulus().bitLength()<2048 || !jwt.verify(new RSASSAVerifier(rsa.toRSAPublicKey())))throw AuthFailure.denied();
        var c=jwt.getJWTClaimsSet();Instant now=clock.instant();
        if(!flow.issuer().equals(c.getIssuer()) || c.getSubject()==null || c.getSubject().isBlank() || !c.getAudience().contains(flow.clientId())
            || (c.getAudience().size()>1 && !flow.clientId().equals(c.getStringClaim("azp"))) || (c.getStringClaim("azp")!=null && !flow.clientId().equals(c.getStringClaim("azp")))
            || c.getExpirationTime()==null || !now.minusSeconds(30).isBefore(c.getExpirationTime().toInstant()) || c.getIssueTime()==null || c.getIssueTime().toInstant().isAfter(now.plusSeconds(30))
            || (c.getNotBeforeTime()!=null && c.getNotBeforeTime().toInstant().isAfter(now.plusSeconds(30))) || !flow.nonce().equals(c.getStringClaim("nonce")))throw AuthFailure.denied();
        // Use Spring's standard OIDC validation extension as well as explicit flow/nonce/purpose checks.
        var registration=ClientRegistration.withRegistrationId(flow.tenant().toString()).clientId(flow.clientId())
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri(flow.redirect()).scope("openid","profile","iserv:groups")
            .issuerUri(flow.issuer()).authorizationUri(text(doc,"authorization_endpoint")).tokenUri(text(doc,"token_endpoint"))
            .jwkSetUri(text(doc,"jwks_uri")).userInfoUri(text(doc,"userinfo_endpoint")).userNameAttributeName("sub").build();
        var standard=new OidcIdTokenValidator(registration);standard.setClock(clock);standard.setClockSkew(Duration.ofSeconds(30));
        var parsed=new HashMap<String,Object>(c.getClaims());parsed.put("iat",c.getIssueTime().toInstant());parsed.put("exp",c.getExpirationTime().toInstant());
        if(c.getNotBeforeTime()!=null)parsed.put("nbf",c.getNotBeforeTime().toInstant());
        if(standard.validate(new Jwt(value,c.getIssueTime().toInstant(),c.getExpirationTime().toInstant(),header.toJSONObject(),parsed)).hasErrors())throw AuthFailure.denied();
        return c;
    }
}
