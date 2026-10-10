package com.github.ksewen.kelios.identity.auth;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AuthSettings {
    private final Environment env;
    public AuthSettings(Environment env){this.env=env;}
    public String value(String name,String fallback){return env.getProperty("kelios.auth."+name,fallback);}
    public URI publicOrigin(){
        URI uri=URI.create(value("public-origin","https://localhost"));
        if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null
            || uri.getFragment()!=null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))throw new AuthFailure(503,"Invalid trusted public origin");
        return URI.create(uri.toString().replaceAll("/$",""));
    }
    public String issuer(){return value("issuer",publicOrigin().toString());}
    public Path keyDirectory(){return Path.of(value("key-directory","/run/secrets/kelios-auth"));}
    public String signingKeyId(){return keyId(value("signing-key-id","main"));}
    public String encryptionKeyId(){return keyId(value("encryption-key-id","main"));}
    public Set<String> trustedProviderHosts(){
        String hosts=value("trusted-provider-hosts","");
        if(hosts.isBlank())return Set.of();
        return Set.copyOf(Arrays.stream(hosts.split(",")).map(String::strip).map(x->x.toLowerCase(Locale.ROOT)).toList());
    }
    public static String keyId(String id){if(id==null || !id.matches("[A-Za-z0-9_-]{1,64}"))throw new AuthFailure(503,"Invalid key identifier");return id;}
    public String callback(UUID tenant){return publicOrigin()+"/api/v1/auth/oidc/"+tenant+"/callback";}
}
