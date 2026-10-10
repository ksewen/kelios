package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.auth.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public record Login(String login,String password){@Override public String toString(){return "Login[redacted]";}}
    public record PasswordChange(String currentPassword,String newPassword){@Override public String toString(){return "PasswordChange[redacted]";}}
    public record Session(String accessToken,long expiresIn,Identity user){@Override public String toString(){return "Session[redacted]";}}
    private final AuthenticationService auth;private final BrowserTokens browser;private final OidcLogin oidc;private final AuthSettings settings;private final LoginThrottle throttle;private final SecurityEvents events;
    public AuthController(AuthenticationService auth,BrowserTokens browser,OidcLogin oidc,AuthSettings settings,LoginThrottle throttle,SecurityEvents events){this.auth=auth;this.browser=browser;this.oidc=oidc;this.settings=settings;this.throttle=throttle;this.events=events;}
    @GetMapping("/csrf") public Map<String,String> csrf(HttpServletRequest request,HttpServletResponse response){return Map.of("token",browser.csrf(request,response));}
    private Session session(HttpServletResponse response,JwtTokens.Pair pair){browser.issue(response,pair);return new Session(pair.accessToken(),pair.expiresIn(),pair.user());}
    @PostMapping("/local/login") public Session login(@RequestBody Login command,HttpServletResponse response){
        String key=command.login()==null?"invalid":command.login().toLowerCase(Locale.ROOT);
        if(key.length()>64)key="invalid";throttle.check("login:"+key,10);return session(response,auth.login(command.login(),command.password()));
    }
    @PostMapping("/session/refresh") public Session refresh(HttpServletRequest request,HttpServletResponse response){return session(response,auth.refresh(browser.cookie(request,BrowserTokens.REFRESH)));}
    @DeleteMapping("/session") public void logout(HttpServletResponse response){browser.clear(response);}
    @GetMapping("/me") public Identity me(){return CurrentIdentity.require();}
    @PutMapping("/local/password") public void password(@RequestBody PasswordChange command){auth.ownPassword(CurrentIdentity.require(),command.currentPassword(),command.newPassword());}
    @PostMapping("/oidc/{tenant}/start") public Map<String,String> start(@PathVariable UUID tenant,HttpServletResponse response){var start=oidc.start(tenant);browser.flow(response,start.flowCookie());return Map.of("authorizationUrl",start.authorizationUrl());}
    @GetMapping("/oidc/{tenant}/callback") public void callback(@PathVariable UUID tenant,@RequestParam(required=false) String state,@RequestParam(required=false) String code,
        @RequestParam(required=false,name="iss")String issuer,@RequestParam(required=false)String error,HttpServletRequest request,HttpServletResponse response)throws IOException{
        try{
            if(error!=null)throw AuthFailure.denied();
            var pair=oidc.callback(tenant,browser.cookie(request,BrowserTokens.FLOW),state,code,issuer);browser.issue(response,pair);browser.clearFlow(response);
            response.setStatus(303);response.setHeader("Location",settings.publicOrigin()+"/auth/complete");
        }catch(AuthFailure ex){events.rejected(ex.status());browser.clearFlow(response);response.setStatus(303);response.setHeader("Location",settings.publicOrigin()+"/auth/failed");}
    }
}
