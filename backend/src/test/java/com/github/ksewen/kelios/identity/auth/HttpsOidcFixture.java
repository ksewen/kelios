package com.github.ksewen.kelios.identity.auth;

import com.sun.net.httpserver.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import tools.jackson.databind.json.JsonMapper;

/** Test-only HTTPS provider reproducing documented IServ UserInfo groups; exercises real code exchange/PKCE. */
final class HttpsOidcFixture implements AutoCloseable {
    record Authorization(String callback,String code,String state){}
    record Pending(String nonce,String challenge,String client,String redirect){}
    final UUID group=UUID.randomUUID();final Path trustStore;final String issuer;final Clock clock;
    final HttpsServer server;final SSLContext ssl;final KeyPair pair;
    final ConcurrentMap<String,Pending> codes=new ConcurrentHashMap<>(),access=new ConcurrentHashMap<>();
    final JsonMapper json=JsonMapper.builder().build();
    volatile String mode="normal",subject="stable-subject",name="Fixture user";
    volatile Object groups;
    HttpsOidcFixture(Path directory,Clock clock)throws Exception{
        this.clock=clock;var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);pair=generator.generateKeyPair();
        X500Name owner=new X500Name("CN=localhost");Instant now=Instant.now();
        var builder=new JcaX509v3CertificateBuilder(owner,BigInteger.valueOf(System.nanoTime()),Date.from(now.minusSeconds(86400)),Date.from(now.plusSeconds(86400*7)),owner,pair.getPublic());
        builder.addExtension(Extension.subjectAlternativeName,false,new GeneralNames(new GeneralName[]{new GeneralName(GeneralName.dNSName,"localhost"),new GeneralName(GeneralName.iPAddress,"127.0.0.1")}));
        X509Certificate cert=new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
        KeyStore keys=KeyStore.getInstance("PKCS12");keys.load(null,null);keys.setKeyEntry("fixture",pair.getPrivate(),"fixture-store".toCharArray(),new java.security.cert.Certificate[]{cert});
        KeyStore trust=KeyStore.getInstance("PKCS12");trust.load(null,null);trust.setCertificateEntry("fixture",cert);trustStore=directory.resolve("provider-trust.p12");try(var out=Files.newOutputStream(trustStore)){trust.store(out,"fixture-store".toCharArray());}
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"fixture-store".toCharArray());var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(trust);
        ssl=SSLContext.getInstance("TLS");ssl.init(km.getKeyManagers(),tm.getTrustManagers(),null);
        server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setHttpsConfigurator(new HttpsConfigurator(ssl));
        server.createContext("/",this::handle);server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());server.start();issuer="https://localhost:"+server.getAddress().getPort();reset();
    }
    void reset(){mode="normal";subject="stable-subject";name="Fixture user";groups=List.of(Map.of("id",group.toString(),"act","members","name","Members"));codes.clear();access.clear();}
    static Map<String,String> query(String query){
        Map<String,String> values=new HashMap<>();if(query==null)return values;for(String item:query.split("&")){var parts=item.split("=",2);values.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),parts.length==1?"":URLDecoder.decode(parts[1],StandardCharsets.UTF_8));}return values;
    }
    Authorization authorize(String url)throws Exception{
        var response=HttpClient.newBuilder().sslContext(ssl).followRedirects(HttpClient.Redirect.NEVER).build().send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.discarding());
        if(response.statusCode()!=302)throw new IllegalStateException("Fixture authorization failed");String callback=response.headers().firstValue("Location").orElseThrow();var args=query(URI.create(callback).getRawQuery());return new Authorization(callback,args.get("code"),args.get("state"));
    }
    private void send(HttpExchange exchange,int status,Object payload)throws IOException{
        byte[] body=json.writeValueAsBytes(payload);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,body.length);try(var output=exchange.getResponseBody()){output.write(body);}
    }
    private void handle(HttpExchange exchange)throws IOException{
        try{
            String path=exchange.getRequestURI().getPath();
            if(path.equals("/.well-known/openid-configuration")){
                send(exchange,200,Map.of("issuer",mode.equals("discovery-issuer")?issuer+"/wrong":issuer,"authorization_endpoint",issuer+"/authorize","token_endpoint",issuer+"/token","userinfo_endpoint",issuer+"/userinfo","jwks_uri",issuer+"/jwks","response_types_supported",List.of("code"),"id_token_signing_alg_values_supported",List.of("RS256"),"code_challenge_methods_supported",List.of(mode.equals("pkce-unsupported")?"plain":"S256"),"token_endpoint_auth_methods_supported",List.of("client_secret_basic")));return;
            }
            if(path.equals("/authorize")){
                var args=query(exchange.getRequestURI().getRawQuery());
                if(!"code".equals(args.get("response_type")) || !"S256".equals(args.get("code_challenge_method")) || !"openid profile iserv:groups".equals(args.get("scope"))){send(exchange,400,Map.of("error","invalid_request"));return;}
                String code=UUID.randomUUID().toString();codes.put(code,new Pending(args.get("nonce"),args.get("code_challenge"),args.get("client_id"),args.get("redirect_uri")));
                exchange.getResponseHeaders().set("Location",args.get("redirect_uri")+"?code="+code+"&state="+args.get("state")+"&iss="+URLEncoder.encode(issuer,StandardCharsets.UTF_8));exchange.sendResponseHeaders(302,-1);exchange.close();return;
            }
            if(path.equals("/jwks")){send(exchange,200,new JWKSet(new RSAKey.Builder((RSAPublicKey)pair.getPublic()).keyID("provider").keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build()).toJSONObject());return;}
            if(path.equals("/token")){
                if(mode.equals("token-unavailable")){send(exchange,503,Map.of("error","unavailable"));return;}
                var args=query(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));var pending=codes.remove(args.get("code"));
                String expected="Basic "+Base64.getEncoder().encodeToString("fixture-client:fixture-secret".getBytes(StandardCharsets.UTF_8));
                String verifier=args.get("code_verifier");String challenge=verifier==null?"":Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
                if(pending==null || !expected.equals(exchange.getRequestHeaders().getFirst("Authorization")) || !pending.challenge().equals(challenge) || !pending.redirect().equals(args.get("redirect_uri"))){send(exchange,400,Map.of("error","invalid_grant"));return;}
                Instant now=clock.instant();var claims=new JWTClaimsSet.Builder().issuer(mode.equals("id-issuer")?issuer+"/wrong":issuer).audience(mode.equals("id-audience")?"wrong-client":pending.client()).subject(subject)
                    .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(mode.equals("id-expired")?-60:300))).claim("nonce",mode.equals("nonce")?"wrong-nonce":pending.nonce()).build();
                SignedJWT id=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("provider").build(),claims);id.sign(new RSASSASigner((RSAPrivateKey)pair.getPrivate()));
                String accessToken=UUID.randomUUID().toString();access.put(accessToken,pending);send(exchange,200,Map.of("token_type","Bearer","access_token",accessToken,"id_token",id.serialize()));return;
            }
            if(path.equals("/userinfo")){
                if(mode.equals("userinfo-unavailable")){send(exchange,503,Map.of("error","unavailable"));return;}
                String auth=exchange.getRequestHeaders().getFirst("Authorization");if(auth==null || !auth.startsWith("Bearer ") || !access.containsKey(auth.substring(7))){send(exchange,401,Map.of("error","invalid_token"));return;}
                Map<String,Object> info=new HashMap<>();info.put("sub",mode.equals("subject-mismatch")?"other-subject":subject);info.put("name",name);info.put("email","mutable@example.test");
                if(groups!=null)info.put("iserv:groups",groups);info.put("iserv:roles",List.of(Map.of("displayName","ADMIN")));send(exchange,200,info);return;
            }
            send(exchange,404,Map.of("error","not_found"));
        }catch(Exception ex){send(exchange,500,Map.of("error","fixture_error"));}
    }
    public void close(){server.stop(0);}
}
