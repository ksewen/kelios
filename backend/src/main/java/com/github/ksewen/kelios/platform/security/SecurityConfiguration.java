package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.auth.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    public static void problem(HttpServletResponse response,int status,String detail)throws IOException{
        response.setStatus(status);response.setContentType("application/problem+json");response.setHeader("Cache-Control","no-store");
        if(status==401)response.setHeader("WWW-Authenticate","Bearer");if(status==429)response.setHeader("Retry-After","60");
        response.getWriter().write(JsonMapper.builder().build().writeValueAsString(Map.of("type","about:blank","title",status==401?"Unauthorized":status==403?"Forbidden":"Request failed","status",status,"detail",detail,"code",status==401?"AUTHENTICATION_REQUIRED":status==403?"FORBIDDEN":status==404?"NOT_FOUND":"INVALID_INPUT")));
    }
    private static boolean identityPath(String path){return path.equals("/api/v1/auth/me") || path.equals("/api/v1/users") || path.startsWith("/api/v1/users/") || path.equals("/api/v1/tenants") || path.matches("/api/v1/tenants/[^/]+") || path.matches("/api/v1/tenants/[^/]+/oidc");}
    private static boolean inventoryRequest(HttpServletRequest r){
        String p=r.getRequestURI(),m=r.getMethod(),id="[^/]+";
        if(m.equals("GET"))return p.matches("/api/v1/(assets|audit-log)(/"+id+"(/events)?)?") || p.matches("/api/v1/tenants/"+id+"/(assets|locations|owners|catalog/(categories|manufacturers|products))") || p.matches("/api/v1/(catalog/(categories|manufacturers|products)|locations|owners)(/"+id+")?");
        if(m.equals("PUT"))return p.matches("/api/v1/assets/"+id+"/(metadata|catalog|loanability|location|owner)") || p.matches("/api/v1/(catalog/(categories|manufacturers|products)|locations|owners)/"+id);
        if(m.equals("DELETE"))return p.matches("/api/v1/(catalog/(categories|manufacturers|products)|locations|owners)/"+id);
        if(m.equals("POST"))return p.matches("/api/v1/tenants/"+id+"/(assets|locations|owners|catalog/(categories|manufacturers|products))") || p.matches("/api/v1/assets/"+id+"/(lifecycle-transitions|condition-transitions|loan|return)") || p.matches("/api/v1/catalog/(categories|manufacturers|products)(/"+id+"/publish)?") || p.matches("/api/v1/catalog/(categories|manufacturers)/(merge-preview|merge)");
        return false;
    }
    private static boolean protectedRequest(HttpServletRequest r){return inventoryRequest(r) || identityPath(r.getRequestURI());}
    @Bean org.springframework.security.core.userdetails.UserDetailsService noDefaultCredential() {
        return name->{throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Use KELIOS authentication");};
    }
    @Bean SecurityFilterChain security(HttpSecurity http,JwtTokens tokens,BrowserTokens browser,LoginThrottle throttle,SecurityEvents events)throws Exception{
        return http.csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/api/v1/status","/actuator/health","/actuator/health/**","/error").permitAll()
                .requestMatchers("/api/v1/auth/me").authenticated().requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(r->inventoryRequest(r)).authenticated().requestMatchers("/api/v1/users","/api/v1/users/**","/api/v1/tenants","/api/v1/tenants/*","/api/v1/tenants/*/oidc").authenticated().anyRequest().denyAll())
            .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->problem(s,protectedRequest(r)?401:404,"Authentication required"))
                .accessDeniedHandler((r,s,x)->problem(s,protectedRequest(r)?403:404,"Access denied")))
            .addFilterBefore(new AuthFilter(tokens,browser,throttle,events),UsernamePasswordAuthenticationFilter.class).build();
    }
    private static class AuthFilter extends OncePerRequestFilter {
        private final SecurityEvents events;
        private final JwtTokens tokens;private final BrowserTokens browser;private final LoginThrottle throttle;
        AuthFilter(JwtTokens tokens,BrowserTokens browser,LoginThrottle throttle,SecurityEvents events){this.tokens=tokens;this.browser=browser;this.throttle=throttle;this.events=events;}
        protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
            try{
                String path=request.getRequestURI();
                if(protectedRequest(request))response.setHeader("Cache-Control","no-store");
                if(path.startsWith("/api/v1/auth/")){
                    response.setHeader("Cache-Control","no-store");
                    if(!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())){
                        throttle.check("address:"+request.getRemoteAddr(),60);browser.requireCsrf(request);
                    }
                    if(path.endsWith("/callback"))throttle.check("callback:"+request.getRemoteAddr(),60);
                }
                String bearer=request.getHeader("Authorization");
                if(bearer!=null){
                    if(!bearer.startsWith("Bearer "))throw AuthFailure.invalid();
                    var identity=tokens.verify(bearer.substring(7),"access").identity();
                    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity,null,List.of()));
                }
                if(protectedRequest(request) && !path.equals("/api/v1/auth/me"))Authorization.active(CurrentIdentity.require());
                if((path.startsWith("/api/v1/auth/") || protectedRequest(request)) && !Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())){
                    // Bound JSON before binding/hash work, including chunked requests without Content-Length.
                    byte[] body=request.getInputStream().readNBytes(32769);
                    if(body.length>32768)throw new AuthFailure(400,"Identity request body exceeds limit");
                    request=new HttpServletRequestWrapper(request){
                        @Override public ServletInputStream getInputStream(){
                            var input=new ByteArrayInputStream(body);
                            return new ServletInputStream(){
                                public int read(){return input.read();}
                                public int read(byte[] bytes,int offset,int length){return input.read(bytes,offset,length);}
                                public boolean isFinished(){return input.available()==0;}
                                public boolean isReady(){return true;}
                                public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Identity endpoints use synchronous request bodies");}
                            };
                        }
                    };
                }
                chain.doFilter(request,response);
            }catch(AuthFailure ex){events.rejected(ex.status());problem(response,ex.status(),ex.getMessage());}
            finally{SecurityContextHolder.clearContext();}
        }
    }
}
