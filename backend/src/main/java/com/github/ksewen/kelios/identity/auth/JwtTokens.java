package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.identity.UserService.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class JwtTokens {
    public record Pair(String accessToken,String refreshToken,long expiresIn,Identity user) {
        @Override public String toString(){return "JwtPair[redacted]";}
    }
    public record Verified(UUID userId,String jti,Instant expiresAt,Identity identity) {}
    private final AuthSettings settings;private final Clock clock;
    public JwtTokens(AuthSettings settings,Clock clock){this.settings=settings;this.clock=clock;}
    private byte[] pem(Path path) throws Exception{return Base64.getDecoder().decode(Files.readString(path).replaceAll("-----[^-]+-----","").replaceAll("\\s", ""));}
    private RSAPrivateKey privateKey() throws Exception {
        var key=(RSAPrivateKey)KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pem(settings.keyDirectory().resolve(settings.signingKeyId()+".private.pem"))));
        if(key.getModulus().bitLength()<3072)throw new IllegalArgumentException();return key;
    }
    private RSAPublicKey publicKey(String id) throws Exception {
        // Only trusted, bounded local key identifiers select mounted public keys; never token URLs.
        if(id==null || !id.matches("[A-Za-z0-9_-]{1,64}"))throw AuthFailure.invalid();
        var key=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pem(settings.keyDirectory().resolve(id+".public.pem"))));
        if(key.getModulus().bitLength()<3072)throw AuthFailure.invalid();return key;
    }
    private String issue(Identity user,String purpose,Duration duration){
        try {
            Instant now=clock.instant();var builder=new JWTClaimsSet.Builder().issuer(settings.issuer()).audience((purpose.equals("access")?"kelios-api":"kelios-refresh")).subject(user.id().toString())
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plus(duration))).jwtID(UUID.randomUUID().toString()).claim("token_use",purpose);
            if(purpose.equals("access"))builder.claim("scope",user.scope().name()).claim("role",user.role().name()).claim("status",user.status().name())
                .claim("tenantId",user.tenantId()==null?null:user.tenantId().toString()).claim("displayName",user.displayName());
            SignedJWT jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(settings.signingKeyId()).type(new JOSEObjectType("kelios-"+purpose+"+jwt")).build(),builder.build());
            jwt.sign(new RSASSASigner(privateKey()));return jwt.serialize();
        }catch(Exception ex){throw new AuthFailure(503,"Signing key unavailable");}
    }
    public Pair login(Identity user){if(user.status()==Status.DISABLED)throw AuthFailure.denied();return new Pair(issue(user,"access",Duration.ofMinutes(15)),issue(user,"refresh",Duration.ofDays(30)),900,user);}
    public Pair refresh(Identity user,String original){if(user.status()==Status.DISABLED)throw AuthFailure.denied();return new Pair(issue(user,"access",Duration.ofMinutes(15)),original,900,user);}
    public Verified verify(String token,String purpose){
        try {
            if(token==null || token.length()>16384)throw AuthFailure.invalid();
            SignedJWT jwt=SignedJWT.parse(token);var header=jwt.getHeader();
            if(!header.getAlgorithm().equals(JWSAlgorithm.RS256) || !new JOSEObjectType("kelios-"+purpose+"+jwt").equals(header.getType())
                || header.getJWKURL()!=null || header.getX509CertURL()!=null || !jwt.verify(new RSASSAVerifier(publicKey(header.getKeyID()))))throw AuthFailure.invalid();
            var c=jwt.getJWTClaimsSet();Instant now=clock.instant();
            if(!settings.issuer().equals(c.getIssuer()) || !c.getAudience().equals(List.of(purpose.equals("access")?"kelios-api":"kelios-refresh")) || !purpose.equals(c.getStringClaim("token_use"))
                || c.getExpirationTime()==null || !now.minusSeconds(30).isBefore(c.getExpirationTime().toInstant())
                || c.getNotBeforeTime()==null || c.getNotBeforeTime().toInstant().isAfter(now.plusSeconds(30))
                || c.getIssueTime()==null || c.getIssueTime().toInstant().isAfter(now.plusSeconds(30)) || c.getJWTID()==null
                || !c.getExpirationTime().after(c.getIssueTime()) || c.getExpirationTime().toInstant().isAfter(c.getIssueTime().toInstant().plus(purpose.equals("access")?Duration.ofMinutes(15):Duration.ofDays(30))))throw AuthFailure.invalid();
            UUID id=UUID.fromString(c.getSubject());Identity user=null;
            if(purpose.equals("access")){
                Scope scope=Scope.valueOf(c.getStringClaim("scope"));String tenant=c.getStringClaim("tenantId");
                user=new Identity(id,scope,tenant==null?null:UUID.fromString(tenant),Role.valueOf(c.getStringClaim("role")),Status.valueOf(c.getStringClaim("status")),c.getStringClaim("displayName"));
                if(user.status()==Status.DISABLED)throw AuthFailure.denied();
            }
            return new Verified(id,c.getJWTID(),c.getExpirationTime().toInstant(),user);
        }catch(AuthFailure ex){throw ex;}catch(Exception ex){throw AuthFailure.invalid();}
    }
}
