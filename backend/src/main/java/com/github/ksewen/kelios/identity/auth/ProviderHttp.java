package com.github.ksewen.kelios.identity.auth;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import javax.net.ssl.*;
import org.apache.hc.client5.http.*;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** All provider requests use verified TLS, bounded IO and a DNS policy at actual connection resolution. */
@Component
public class ProviderHttp implements AutoCloseable {
    private final AuthSettings settings;private final CloseableHttpClient client;
    private final JsonMapper json=JsonMapper.builder().build();
    public ProviderHttp(AuthSettings settings){
        this.settings=settings;
        DnsResolver resolver=new DnsResolver(){
            public InetAddress[] resolve(String host)throws UnknownHostException{
                InetAddress[] addresses=InetAddress.getAllByName(host);
                for(var ip:addresses)if(blocked(ip) && !settings.trustedProviderHosts().contains(host.toLowerCase(Locale.ROOT)))throw new UnknownHostException("Provider address is not trusted");
                return addresses;
            }
            public String resolveCanonicalHostname(String host)throws UnknownHostException{return host;}
        };
        var tls=ClientTlsStrategyBuilder.create().setSslContext(sslContext()).buildClassic();
        var manager=PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(resolver).setTlsSocketStrategy(tls).setMaxConnTotal(16).setMaxConnPerRoute(4).build();
        client=HttpClients.custom().setConnectionManager(manager).disableRedirectHandling().disableAutomaticRetries().disableCookieManagement().disableContentCompression()
            .setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofSeconds(5)).setConnectTimeout(Timeout.ofSeconds(5)).setResponseTimeout(Timeout.ofSeconds(10)).build()).build();
    }
    private SSLContext sslContext(){
        try {
            String file=settings.value("provider-trust-store","");if(file.isEmpty())return SSLContext.getDefault();
            KeyStore store=KeyStore.getInstance("PKCS12");
            try(var in=Files.newInputStream(Path.of(file))){store.load(in,settings.value("provider-trust-store-password","").toCharArray());}
            var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(store);
            SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(null,tm.getTrustManagers(),null);return ssl;
        }catch(Exception ex){throw new IllegalStateException("Invalid provider TLS trust configuration");}
    }
    static boolean blocked(InetAddress address){
        byte[] b=address.getAddress();
        return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()
            || (b.length==16 && (b[0]&0xfe)==0xfc) || (b.length==4 && ((b[0]&255)==0 || ((b[0]&255)==100 && ((b[1]&255)>=64 && (b[1]&255)<=127)) || (b[0]&255)>=224));
    }
    public URI endpoint(String url,String issuer){
        try {
            URI uri=OidcConfigurationService.issuerUri(url),base=OidcConfigurationService.issuerUri(issuer);
            if(!uri.getHost().equalsIgnoreCase(base.getHost()) && !settings.trustedProviderHosts().contains(uri.getHost().toLowerCase(Locale.ROOT)))throw AuthFailure.provider();
            return uri;
        }catch(RuntimeException ex){throw AuthFailure.provider();}
    }
    public Map<String,Object> get(String url,String issuer,String bearer){
        var request=new HttpGet(endpoint(url,issuer));if(bearer!=null)request.setHeader("Authorization","Bearer "+bearer);return execute(request);
    }
    public Map<String,Object> post(String url,String issuer,String body,String basic){
        var request=new HttpPost(endpoint(url,issuer));request.setEntity(new StringEntity(body,ContentType.APPLICATION_FORM_URLENCODED));if(basic!=null)request.setHeader("Authorization","Basic "+basic);return execute(request);
    }
    @SuppressWarnings("unchecked") private Map<String,Object> execute(HttpUriRequestBase request){
        try {
            request.setHeader("Accept","application/json");
            return client.execute(request,response->{
                if(response.getCode()!=200 || response.getEntity()==null)throw new IOException("Provider failure");
                try(var in=response.getEntity().getContent()){
                    byte[] body=in.readNBytes(131073);if(body.length>131072)throw new IOException("Provider response limit");
                    Object value=json.readValue(body,Object.class);if(!(value instanceof Map<?,?>))throw new IOException("Provider response shape");return (Map<String,Object>)value;
                }
            });
        }catch(Exception ex){throw AuthFailure.provider();}
    }
    public void close()throws IOException{client.close();}
}
