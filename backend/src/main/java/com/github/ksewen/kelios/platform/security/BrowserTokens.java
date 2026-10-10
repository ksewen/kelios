package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.auth.*;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class BrowserTokens {
    public static final String REFRESH="__Secure-kelios-refresh",CSRF="__Secure-kelios-csrf",FLOW="__Secure-kelios-oidc";
    private static final String PATH="/api/v1/auth/";
    private final SecretEnvelope secrets;private final AuthSettings settings;private final JwtTokens tokens;private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    public BrowserTokens(SecretEnvelope secrets,AuthSettings settings,JwtTokens tokens,Clock clock){this.secrets=secrets;this.settings=settings;this.tokens=tokens;this.clock=clock;}
    public String cookie(HttpServletRequest request,String name){
        String value=null;for(Cookie c:request.getCookies()==null?new Cookie[0]:request.getCookies())if(c.getName().equals(name)){
            if(value!=null)throw AuthFailure.invalid();value=c.getValue();
        }return value;
    }
    public void set(HttpServletResponse response,String name,String value,long seconds,String sameSite){
        response.addHeader("Set-Cookie",ResponseCookie.from(name,value).path(PATH).secure(true).httpOnly(true).sameSite(sameSite).maxAge(seconds).build().toString());
        response.setHeader("Cache-Control","no-store");
    }
    public void issue(HttpServletResponse response,JwtTokens.Pair pair){
        long remaining=Math.max(0,Duration.between(clock.instant(),tokens.verify(pair.refreshToken(),"refresh").expiresAt()).getSeconds());
        set(response,REFRESH,pair.refreshToken(),remaining,"Strict");
    }
    public void flow(HttpServletResponse response,String value){set(response,FLOW,value,300,"Lax");}
    public void clearFlow(HttpServletResponse response){set(response,FLOW,"",0,"Lax");}
    public void clear(HttpServletResponse response){set(response,REFRESH,"",0,"Strict");set(response,CSRF,"",0,"Strict");clearFlow(response);}
    private String binding(HttpServletRequest request){
        String refresh=cookie(request,REFRESH);if(refresh==null)return "anonymous";
        // Expired/invalid cookies must not prevent a clean new login/logout; use anonymous proof then.
        try{return tokens.verify(refresh,"refresh").jti();}catch(AuthFailure ex){return "anonymous";}
    }
    public String csrf(HttpServletRequest request,HttpServletResponse response){
        byte[] bytes=new byte[32];random.nextBytes(bytes);String nonce=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String body=clock.instant().plusSeconds(1800).getEpochSecond()+":"+nonce+":"+binding(request);
        String proof=Base64.getUrlEncoder().withoutPadding().encodeToString(body.getBytes(StandardCharsets.UTF_8))+"~"+secrets.sign(body);
        set(response,CSRF,proof,1800,"Strict");return proof;
    }
    public void requireCsrf(HttpServletRequest request){
        if(!settings.publicOrigin().toString().equals(request.getHeader("Origin")))throw AuthFailure.denied();
        String proof=request.getHeader("X-CSRF-Token"),cookie=cookie(request,CSRF);
        if(proof==null || cookie==null || proof.length()>2048 || !MessageDigest.isEqual(proof.getBytes(StandardCharsets.UTF_8),cookie.getBytes(StandardCharsets.UTF_8)))throw AuthFailure.denied();
        try {
            String[] parts=proof.split("~",-1);if(parts.length!=2)throw AuthFailure.denied();
            String body=new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8);String[] fields=body.split(":",-1);
            if(fields.length!=3 || Long.parseLong(fields[0])<=clock.instant().getEpochSecond() || Long.parseLong(fields[0])>clock.instant().plusSeconds(1800).getEpochSecond()
                || !fields[2].equals(binding(request)) || !secrets.verify(body,parts[1]))throw AuthFailure.denied();
        }catch(AuthFailure ex){throw ex;}catch(RuntimeException ex){throw AuthFailure.denied();}
    }
}
